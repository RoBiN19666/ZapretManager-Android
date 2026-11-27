/*
 * Прямой JNI-мост к Go-рантайму TG WS Proxy (libtgwsproxy.so, c-shared).
 *
 * Раньше использовалась JNA, но она тянет libffi/libjnidispatch и на новых
 * версиях Android (16 КБ страницы, Android 16) процесс :tg падал без следа.
 * Здесь тот же вызов, но через dlopen + dlsym, без сторонних прослоек.
 */

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>

#define TG_TAG "ZmTg"

static pthread_mutex_t tg_lock = PTHREAD_MUTEX_INITIALIZER;
static void *tg_lib = NULL;
static int tg_load_state = 0; /* 0 — не пробовали, 1 — загружено, -1 — ошибка */
static char tg_error[512];

typedef int (*tg_start_fn)(const char *, int, const char *, const char *, int);
typedef int (*tg_stop_fn)(void);
typedef char *(*tg_status_fn)(void);
typedef void (*tg_free_fn)(char *);
typedef void (*tg_set_cf_fn)(const char *);

static tg_start_fn tg_start = NULL;
static tg_stop_fn tg_stop = NULL;
static tg_status_fn tg_status = NULL;
static tg_free_fn tg_free = NULL;
static tg_set_cf_fn tg_set_cf = NULL;

static int tg_ensure(void) {
    pthread_mutex_lock(&tg_lock);
    if (tg_load_state != 0) {
        int result = tg_load_state > 0 ? 0 : -1;
        pthread_mutex_unlock(&tg_lock);
        return result;
    }

    void *library = dlopen("libtgwsproxy.so", RTLD_NOW | RTLD_LOCAL);
    if (library == NULL) {
        snprintf(tg_error, sizeof(tg_error), "dlopen: %s", dlerror());
        tg_load_state = -1;
        pthread_mutex_unlock(&tg_lock);
        __android_log_print(ANDROID_LOG_ERROR, TG_TAG, "%s", tg_error);
        return -1;
    }

    tg_start = (tg_start_fn) dlsym(library, "StartMtProtoProxy");
    tg_stop = (tg_stop_fn) dlsym(library, "StopMtProtoProxy");
    tg_status = (tg_status_fn) dlsym(library, "GetMtProtoProxyStatus");
    tg_free = (tg_free_fn) dlsym(library, "FreeString");
    tg_set_cf = (tg_set_cf_fn) dlsym(library, "SetCachedCFDomains");

    if (tg_start == NULL || tg_stop == NULL || tg_status == NULL || tg_free == NULL) {
        snprintf(tg_error, sizeof(tg_error), "dlsym: экспортируемые функции не найдены");
        dlclose(library);
        tg_load_state = -1;
        pthread_mutex_unlock(&tg_lock);
        __android_log_print(ANDROID_LOG_ERROR, TG_TAG, "%s", tg_error);
        return -1;
    }

    tg_lib = library;
    tg_load_state = 1;
    pthread_mutex_unlock(&tg_lock);
    __android_log_print(ANDROID_LOG_INFO, TG_TAG, "libtgwsproxy.so загружена");
    return 0;
}

JNIEXPORT jint JNICALL
Java_io_github_robin19666_zapretmanager_tgproxy_TgNative_nativeEnsure(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return tg_ensure();
}

JNIEXPORT jint JNICALL
Java_io_github_robin19666_zapretmanager_tgproxy_TgNative_startProxyNative(
        JNIEnv *env, jclass clazz, jstring host, jint port, jstring secret,
        jstring config, jint verbose) {
    (void) clazz;
    if (tg_ensure() != 0) return -1;

    const char *c_host = (*env)->GetStringUTFChars(env, host, NULL);
    const char *c_secret = (*env)->GetStringUTFChars(env, secret, NULL);
    const char *c_config = (*env)->GetStringUTFChars(env, config, NULL);
    int result = tg_start(c_host, (int) port, c_secret, c_config, (int) verbose);
    __android_log_print(ANDROID_LOG_INFO, TG_TAG, "StartMtProtoProxy -> %d", result);
    (*env)->ReleaseStringUTFChars(env, host, c_host);
    (*env)->ReleaseStringUTFChars(env, secret, c_secret);
    (*env)->ReleaseStringUTFChars(env, config, c_config);
    return result;
}

JNIEXPORT jint JNICALL
Java_io_github_robin19666_zapretmanager_tgproxy_TgNative_stopProxyNative(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    if (tg_ensure() != 0) return -1;
    int result = tg_stop();
    __android_log_print(ANDROID_LOG_INFO, TG_TAG, "StopMtProtoProxy -> %d", result);
    return result;
}

JNIEXPORT jstring JNICALL
Java_io_github_robin19666_zapretmanager_tgproxy_TgNative_statusNative(JNIEnv *env, jclass clazz) {
    (void) clazz;
    if (tg_ensure() != 0) return NULL;
    char *raw = tg_status();
    if (raw == NULL) return NULL;
    jstring result = (*env)->NewStringUTF(env, raw);
    tg_free(raw);
    return result;
}

JNIEXPORT void JNICALL
Java_io_github_robin19666_zapretmanager_tgproxy_TgNative_setCachedDomainsNative(
        JNIEnv *env, jclass clazz, jstring domains) {
    (void) clazz;
    if (tg_ensure() != 0 || tg_set_cf == NULL) return;
    const char *c_domains = (*env)->GetStringUTFChars(env, domains, NULL);
    tg_set_cf(c_domains);
    (*env)->ReleaseStringUTFChars(env, domains, c_domains);
}

JNIEXPORT jstring JNICALL
Java_io_github_robin19666_zapretmanager_tgproxy_TgNative_lastErrorNative(JNIEnv *env, jclass clazz) {
    (void) clazz;
    pthread_mutex_lock(&tg_lock);
    char buffer[512];
    snprintf(buffer, sizeof(buffer), "%s", tg_error);
    pthread_mutex_unlock(&tg_lock);
    return (*env)->NewStringUTF(env, buffer);
}
