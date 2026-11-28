/*
 * JNI-мост к byedpi (hufrea/byedpi) и tun2socks (heiher/hev-socks5-tunnel).
 *
 * byedpi — программа с блокирующим main(): корректный способ остановки —
 * shutdown() слушающего сокета. Повторный запуск в том же процессе
 * невозможен, поэтому сервис живёт в отдельном процессе :native, который
 * при остановке завершается целиком.
 */

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>

#define TAG "ZmNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* Из byedpi/main.c */
extern int server_fd;
int main(int argc, char **argv);

typedef struct {
    char *config;
    int fd;
} tunnel_args;

static pthread_mutex_t state_lock = PTHREAD_MUTEX_INITIALIZER;
static int byedpi_started = 0;
static int byedpi_running = 0;

static pthread_t tunnel_thread;
static int tunnel_thread_valid = 0;
static int tunnel_running = 0;
static void *hev_library = NULL;
static int (*hev_main_from_str)(const unsigned char *, unsigned int, int) = NULL;
static void (*hev_quit)(void) = NULL;

static char last_error[512];

/* Заглушка вместо daemon(): в bionic её нет, а byedpi умеет её вызывать. */
int __wrap_daemon(int nochdir, int noclose) {
    (void) nochdir;
    (void) noclose;
    return 0;
}

static void set_error(const char *message) {
    pthread_mutex_lock(&state_lock);
    snprintf(last_error, sizeof(last_error), "%s", message ? message : "");
    pthread_mutex_unlock(&state_lock);
    LOGE("%s", message ? message : "");
}

static void clear_error(void) {
    pthread_mutex_lock(&state_lock);
    last_error[0] = '\0';
    pthread_mutex_unlock(&state_lock);
}

static void free_argv(char **argv) {
    if (argv == NULL) return;
    for (int i = 0; argv[i] != NULL; i++) free(argv[i]);
    free(argv);
}

static void *byedpi_thread(void *arg) {
    char **argv = (char **) arg;
    int argc = 0;
    while (argv[argc] != NULL) argc++;
    LOGI("byedpi: start (argc=%d)", argc);
    int result = main(argc, argv);
    LOGI("byedpi: exited with %d", result);
    pthread_mutex_lock(&state_lock);
    byedpi_running = 0;
    pthread_mutex_unlock(&state_lock);
    return NULL;
}

JNIEXPORT jint JNICALL
Java_io_github_robin19666_zapretmanager_byedpi_Native_startByedpi(JNIEnv *env, jclass clazz,
                                                                 jobjectArray args) {
    (void) clazz;
    pthread_mutex_lock(&state_lock);
    if (byedpi_started) {
        pthread_mutex_unlock(&state_lock);
        set_error("byedpi уже запускался в этом процессе");
        return -1;
    }
    pthread_mutex_unlock(&state_lock);

    clear_error();

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = (char **) calloc((size_t) argc + 1, sizeof(char *));
    if (argv == NULL) {
        set_error("нет памяти под аргументы byedpi");
        return -2;
    }
    for (int i = 0; i < argc; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        if (item == NULL) {
            free_argv(argv);
            set_error("не удалось прочитать аргументы byedpi");
            return -2;
        }
        const char *utf = (*env)->GetStringUTFChars(env, item, NULL);
        if (utf == NULL) {
            (*env)->DeleteLocalRef(env, item);
            free_argv(argv);
            set_error("нет памяти под аргументы byedpi");
            return -2;
        }
        argv[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, item, utf);
        (*env)->DeleteLocalRef(env, item);
        if (argv[i] == NULL) {
            free_argv(argv);
            set_error("нет памяти под аргументы byedpi");
            return -2;
        }
    }

    pthread_t thread;
    pthread_mutex_lock(&state_lock);
    // byedpi_started ставим только после успешной подготовки аргументов:
    // иначе неудачный старт (например, нехватка памяти) навсегда запрещал
    // повторный запуск движка в этом процессе.
    byedpi_started = 1;
    server_fd = -1;
    byedpi_running = 1;
    pthread_mutex_unlock(&state_lock);

    if (pthread_create(&thread, NULL, byedpi_thread, argv) != 0) {
        pthread_mutex_lock(&state_lock);
        byedpi_started = 0;
        byedpi_running = 0;
        pthread_mutex_unlock(&state_lock);
        free_argv(argv);
        set_error("не удалось создать поток byedpi");
        return -3;
    }
    pthread_detach(thread);
    return 0;
}

JNIEXPORT jint JNICALL
Java_io_github_robin19666_zapretmanager_byedpi_Native_stopByedpi(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    int result = -1;
    pthread_mutex_lock(&state_lock);
    if (byedpi_running && server_fd >= 0) {
        shutdown(server_fd, SHUT_RDWR);
        result = 0;
    }
    pthread_mutex_unlock(&state_lock);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_io_github_robin19666_zapretmanager_byedpi_Native_byedpiRunning(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    pthread_mutex_lock(&state_lock);
    jboolean running = byedpi_running ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&state_lock);
    return running;
}

JNIEXPORT jboolean JNICALL
Java_io_github_robin19666_zapretmanager_byedpi_Native_tunnelRunning(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    pthread_mutex_lock(&state_lock);
    jboolean running = tunnel_running ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&state_lock);
    return running;
}

static void *tunnel_thread_body(void *arg) {
    tunnel_args *params = (tunnel_args *) arg;
    LOGI("hev-socks5-tunnel: start fd=%d", params->fd);
    int result = hev_main_from_str((const unsigned char *) params->config,
                                   (unsigned int) strlen(params->config), params->fd);
    LOGI("hev-socks5-tunnel: exited with %d", result);
    free(params->config);
    free(params);
    pthread_mutex_lock(&state_lock);
    tunnel_running = 0;
    pthread_mutex_unlock(&state_lock);
    return NULL;
}

static int load_hev(void) {
    pthread_mutex_lock(&state_lock);
    if (hev_library != NULL) {
        pthread_mutex_unlock(&state_lock);
        return 0;
    }
    pthread_mutex_unlock(&state_lock);

    void *library = dlopen("libhev-socks5-tunnel.so", RTLD_NOW | RTLD_LOCAL);
    if (library == NULL) {
        char message[512];
        snprintf(message, sizeof(message), "dlopen libhev-socks5-tunnel.so: %s", dlerror());
        set_error(message);
        return -1;
    }
    int (*tunnel_main)(const unsigned char *, unsigned int, int) =
            (int (*)(const unsigned char *, unsigned int, int)) dlsym(library,
                                                                     "hev_socks5_tunnel_main_from_str");
    void (*tunnel_quit)(void) = (void (*)(void)) dlsym(library, "hev_socks5_tunnel_quit");
    if (tunnel_main == NULL || tunnel_quit == NULL) {
        dlclose(library);
        set_error("в libhev-socks5-tunnel.so нет нужных символов");
        return -2;
    }
    pthread_mutex_lock(&state_lock);
    hev_library = library;
    hev_main_from_str = tunnel_main;
    hev_quit = tunnel_quit;
    pthread_mutex_unlock(&state_lock);
    return 0;
}

/* Запускает tun2socks с уже открытым tun-fd в отдельном потоке. */
JNIEXPORT jint JNICALL
Java_io_github_robin19666_zapretmanager_byedpi_Native_startTunnel(JNIEnv *env, jclass clazz,
                                                                  jstring config, jint fd) {
    (void) clazz;
    if (load_hev() != 0) return -1;

    pthread_mutex_lock(&state_lock);
    if (tunnel_thread_valid) {
        pthread_mutex_unlock(&state_lock);
        set_error("tun2socks уже запущен");
        return -2;
    }
    tunnel_thread_valid = 1;
    tunnel_running = 1;
    pthread_mutex_unlock(&state_lock);

    const char *utf = (*env)->GetStringUTFChars(env, config, NULL);
    if (utf == NULL) {
        pthread_mutex_lock(&state_lock);
        tunnel_thread_valid = 0;
        tunnel_running = 0;
        pthread_mutex_unlock(&state_lock);
        set_error("не удалось прочитать конфигурацию tun2socks");
        return -3;
    }
    tunnel_args *params = (tunnel_args *) calloc(1, sizeof(tunnel_args));
    if (params == NULL) {
        (*env)->ReleaseStringUTFChars(env, config, utf);
        pthread_mutex_lock(&state_lock);
        tunnel_thread_valid = 0;
        tunnel_running = 0;
        pthread_mutex_unlock(&state_lock);
        set_error("нет памяти под параметры tun2socks");
        return -3;
    }
    params->config = strdup(utf);
    params->fd = (int) fd;
    (*env)->ReleaseStringUTFChars(env, config, utf);
    if (params->config == NULL) {
        free(params);
        pthread_mutex_lock(&state_lock);
        tunnel_thread_valid = 0;
        tunnel_running = 0;
        pthread_mutex_unlock(&state_lock);
        set_error("нет памяти под конфигурацию tun2socks");
        return -3;
    }

    if (pthread_create(&tunnel_thread, NULL, tunnel_thread_body, params) != 0) {
        free(params->config);
        free(params);
        pthread_mutex_lock(&state_lock);
        tunnel_thread_valid = 0;
        tunnel_running = 0;
        pthread_mutex_unlock(&state_lock);
        set_error("не удалось создать поток tun2socks");
        return -4;
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_io_github_robin19666_zapretmanager_byedpi_Native_stopTunnel(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    pthread_mutex_lock(&state_lock);
    void (*quit_fn)(void) = hev_quit;
    int has_thread = tunnel_thread_valid;
    pthread_t thread = tunnel_thread;
    pthread_mutex_unlock(&state_lock);

    if (quit_fn != NULL) quit_fn();

    if (!has_thread) return;

    // pthread_join без таймаута вешал поток остановки сервиса, если hev не
    // выходил по quit(): ждём его завершения ограниченное время.
    int waited_ms = 0;
    int finished = 0;
    while (waited_ms < 2000) {
        pthread_mutex_lock(&state_lock);
        finished = !tunnel_running;
        pthread_mutex_unlock(&state_lock);
        if (finished) break;
        usleep(100 * 1000);
        waited_ms += 100;
    }

    if (finished) {
        pthread_join(thread, NULL);
        pthread_mutex_lock(&state_lock);
        tunnel_thread_valid = 0;
        pthread_mutex_unlock(&state_lock);
        return;
    }

    // Не дождались: поток остаётся, но процесс :native всё равно завершается
    // следом, а флаг valid не даёт запустить второй tun2socks поверх первого.
    LOGW("hev-socks5-tunnel не завершился за %d мс — оставляю поток", waited_ms);
}

JNIEXPORT jstring JNICALL
Java_io_github_robin19666_zapretmanager_byedpi_Native_lastError(JNIEnv *env, jclass clazz) {
    (void) clazz;
    pthread_mutex_lock(&state_lock);
    char buffer[512];
    snprintf(buffer, sizeof(buffer), "%s", last_error);
    pthread_mutex_unlock(&state_lock);
    return (*env)->NewStringUTF(env, buffer);
}

JNIEXPORT jstring JNICALL
Java_io_github_robin19666_zapretmanager_byedpi_Native_buildInfo(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, "byedpi + hev-socks5-tunnel (NDK)");
}
