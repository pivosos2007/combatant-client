/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <jni.h>

#include <mutex>
#include <sstream>
#include <string>

#if defined(_WIN32)
#include <windows.h>
#include <aclapi.h>
#elif defined(__linux__)
#include <sys/prctl.h>
#include <sys/resource.h>
#include <cerrno>
#include <cstring>
#endif

namespace {
constexpr jint WINDOWS_PROCESS_DACL = 1;
constexpr jint CORE_DUMPS_DISABLED = 1 << 1;
constexpr jint LINUX_NONDUMPABLE = 1 << 2;

std::mutex state_mutex;
bool apply_attempted = false;
bool shutdown_attempted = false;
jint applied_mask = 0;
std::string last_error;

#if defined(_WIN32)
PSECURITY_DESCRIPTOR original_security_descriptor = nullptr;
PACL original_dacl = nullptr;
bool original_dacl_protected = false;
bool original_dacl_captured = false;
#elif defined(__linux__)
rlimit original_core_limit{};
bool original_core_limit_captured = false;
int original_dumpable = -1;
#endif

void append_error(const std::string& message) {
    if (!last_error.empty()) last_error.append("; ");
    last_error.append(message);
}

#if defined(_WIN32)
std::string windows_error(const char* operation, DWORD error) {
    std::ostringstream message;
    message << operation << " failed with Windows error " << error;
    return message.str();
}

bool capture_windows_process_dacl() {
    if (original_dacl_captured) return true;

    DWORD result = GetSecurityInfo(
            GetCurrentProcess(),
            SE_KERNEL_OBJECT,
            DACL_SECURITY_INFORMATION,
            nullptr,
            nullptr,
            &original_dacl,
            nullptr,
            &original_security_descriptor
    );
    if (result != ERROR_SUCCESS) {
        append_error(windows_error("GetSecurityInfo", result));
        return false;
    }

    SECURITY_DESCRIPTOR_CONTROL control = 0;
    DWORD revision = 0;
    if (!GetSecurityDescriptorControl(original_security_descriptor, &control, &revision)) {
        append_error(windows_error("GetSecurityDescriptorControl", GetLastError()));
        LocalFree(original_security_descriptor);
        original_security_descriptor = nullptr;
        original_dacl = nullptr;
        return false;
    }

    original_dacl_protected = (control & SE_DACL_PROTECTED) != 0;
    original_dacl_captured = true;
    return true;
}

void apply_windows_process_dacl() {
    if (!capture_windows_process_dacl()) return;

    HANDLE token = nullptr;

    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) {
        append_error(windows_error("OpenProcessToken", GetLastError()));
        return;
    }

    DWORD token_user_size = 0;
    GetTokenInformation(token, TokenUser, nullptr, 0, &token_user_size);

    if (token_user_size == 0) {
        append_error(windows_error("GetTokenInformation(size)", GetLastError()));
        CloseHandle(token);
        return;
    }

    auto* token_user = static_cast<TOKEN_USER*>(LocalAlloc(LPTR, token_user_size));

    if (token_user == nullptr) {
        append_error("LocalAlloc for TokenUser failed");
        CloseHandle(token);
        return;
    }

    if (!GetTokenInformation(token, TokenUser, token_user, token_user_size, &token_user_size)) {
        append_error(windows_error("GetTokenInformation", GetLastError()));
        LocalFree(token_user);
        CloseHandle(token);
        return;
    }

    BYTE system_sid_buffer[SECURITY_MAX_SID_SIZE];
    BYTE administrators_sid_buffer[SECURITY_MAX_SID_SIZE];
    DWORD system_sid_size = sizeof(system_sid_buffer);
    DWORD administrators_sid_size = sizeof(administrators_sid_buffer);

    if (!CreateWellKnownSid(WinLocalSystemSid, nullptr, system_sid_buffer, &system_sid_size)
            || !CreateWellKnownSid(WinBuiltinAdministratorsSid, nullptr,
                    administrators_sid_buffer, &administrators_sid_size)) {
        append_error(windows_error("CreateWellKnownSid", GetLastError()));
        LocalFree(token_user);
        CloseHandle(token);
        return;
    }

    constexpr DWORD dangerous_access = PROCESS_CREATE_THREAD
            | PROCESS_VM_OPERATION
            | PROCESS_VM_READ
            | PROCESS_VM_WRITE
            | PROCESS_DUP_HANDLE
            | PROCESS_CREATE_PROCESS
            | PROCESS_SET_QUOTA
            | PROCESS_SET_INFORMATION
            | PROCESS_SUSPEND_RESUME
            | WRITE_DAC
            | WRITE_OWNER;
    EXPLICIT_ACCESSW entries[4]{};

    entries[0].grfAccessPermissions = dangerous_access;
    entries[0].grfAccessMode = DENY_ACCESS;
    entries[0].grfInheritance = NO_INHERITANCE;
    entries[0].Trustee.TrusteeForm = TRUSTEE_IS_SID;
    entries[0].Trustee.TrusteeType = TRUSTEE_IS_USER;
    entries[0].Trustee.ptstrName = static_cast<LPWSTR>(token_user->User.Sid);

    // PROCESS_TERMINATE is intentionally allowed. The old ACL replaced the complete process DACL
    // but omitted this right from the allow entry, which made normal same-user launcher/process
    // termination fail even though PROCESS_TERMINATE was not part of dangerous_access.
    entries[1].grfAccessPermissions = PROCESS_TERMINATE
            | PROCESS_QUERY_LIMITED_INFORMATION
            | SYNCHRONIZE;
    entries[1].grfAccessMode = GRANT_ACCESS;
    entries[1].grfInheritance = NO_INHERITANCE;
    entries[1].Trustee.TrusteeForm = TRUSTEE_IS_SID;
    entries[1].Trustee.TrusteeType = TRUSTEE_IS_USER;
    entries[1].Trustee.ptstrName = static_cast<LPWSTR>(token_user->User.Sid);

    entries[2].grfAccessPermissions = PROCESS_ALL_ACCESS;
    entries[2].grfAccessMode = GRANT_ACCESS;
    entries[2].grfInheritance = NO_INHERITANCE;
    entries[2].Trustee.TrusteeForm = TRUSTEE_IS_SID;
    entries[2].Trustee.TrusteeType = TRUSTEE_IS_WELL_KNOWN_GROUP;
    entries[2].Trustee.ptstrName = reinterpret_cast<LPWSTR>(system_sid_buffer);

    entries[3].grfAccessPermissions = PROCESS_ALL_ACCESS;
    entries[3].grfAccessMode = GRANT_ACCESS;
    entries[3].grfInheritance = NO_INHERITANCE;
    entries[3].Trustee.TrusteeForm = TRUSTEE_IS_SID;
    entries[3].Trustee.TrusteeType = TRUSTEE_IS_GROUP;
    entries[3].Trustee.ptstrName = reinterpret_cast<LPWSTR>(administrators_sid_buffer);

    PACL dacl = nullptr;
    DWORD result = SetEntriesInAclW(4, entries, nullptr, &dacl);

    if (result == ERROR_SUCCESS) {
        result = SetSecurityInfo(
                GetCurrentProcess(),
                SE_KERNEL_OBJECT,
                DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION,
                nullptr,
                nullptr,
                dacl,
                nullptr
        );
    }

    if (result == ERROR_SUCCESS) {
        applied_mask |= WINDOWS_PROCESS_DACL;
    } else {
        append_error(windows_error("SetSecurityInfo", result));
    }

    if (dacl != nullptr) LocalFree(dacl);
    LocalFree(token_user);
    CloseHandle(token);
}

void restore_windows_process_dacl() {
    if (!original_dacl_captured || original_security_descriptor == nullptr) return;

    SECURITY_INFORMATION info = DACL_SECURITY_INFORMATION
            | (original_dacl_protected
                    ? PROTECTED_DACL_SECURITY_INFORMATION
                    : UNPROTECTED_DACL_SECURITY_INFORMATION);
    DWORD result = SetSecurityInfo(
            GetCurrentProcess(),
            SE_KERNEL_OBJECT,
            info,
            nullptr,
            nullptr,
            original_dacl,
            nullptr
    );

    if (result == ERROR_SUCCESS) {
        applied_mask &= ~WINDOWS_PROCESS_DACL;
        LocalFree(original_security_descriptor);
        original_security_descriptor = nullptr;
        original_dacl = nullptr;
        original_dacl_captured = false;
    } else {
        append_error(windows_error("restore SetSecurityInfo", result));
    }
}
#elif defined(__linux__)
void apply_linux_hardening() {
    rlimit current_core_limit{};
    if (getrlimit(RLIMIT_CORE, &current_core_limit) == 0) {
        original_core_limit = current_core_limit;
        original_core_limit_captured = true;

        // Only lower the soft limit. Lowering rlim_max to zero is irreversible for an
        // unprivileged process and made a real shutdown-time restoration impossible.
        rlimit hardened_core_limit = current_core_limit;
        hardened_core_limit.rlim_cur = 0;
        if (setrlimit(RLIMIT_CORE, &hardened_core_limit) == 0) {
            applied_mask |= CORE_DUMPS_DISABLED;
        } else {
            append_error(std::string("setrlimit(RLIMIT_CORE) failed: ") + std::strerror(errno));
        }
    } else {
        append_error(std::string("getrlimit(RLIMIT_CORE) failed: ") + std::strerror(errno));
    }

    original_dumpable = prctl(PR_GET_DUMPABLE, 0, 0, 0, 0);
    if (original_dumpable < 0) {
        append_error(std::string("prctl(PR_GET_DUMPABLE) failed: ") + std::strerror(errno));
    } else if (prctl(PR_SET_DUMPABLE, 0, 0, 0, 0) == 0) {
        applied_mask |= LINUX_NONDUMPABLE;
    } else {
        append_error(std::string("prctl(PR_SET_DUMPABLE) failed: ") + std::strerror(errno));
    }
}

void restore_linux_hardening() {
    if ((applied_mask & CORE_DUMPS_DISABLED) != 0 && original_core_limit_captured) {
        if (setrlimit(RLIMIT_CORE, &original_core_limit) == 0) {
            applied_mask &= ~CORE_DUMPS_DISABLED;
        } else {
            append_error(std::string("restore setrlimit(RLIMIT_CORE) failed: ") + std::strerror(errno));
        }
    }

    if ((applied_mask & LINUX_NONDUMPABLE) != 0 && original_dumpable >= 0) {
        if (prctl(PR_SET_DUMPABLE, original_dumpable, 0, 0, 0) == 0) {
            applied_mask &= ~LINUX_NONDUMPABLE;
        } else {
            append_error(std::string("restore prctl(PR_SET_DUMPABLE) failed: ") + std::strerror(errno));
        }
    }
}
#endif

void apply_hardening() {
    std::lock_guard<std::mutex> lock(state_mutex);
    if (apply_attempted) return;
    apply_attempted = true;

#if defined(_WIN32)
    apply_windows_process_dacl();
#elif defined(__linux__)
    apply_linux_hardening();
#else
    append_error("unsupported operating system");
#endif
}

void shutdown_hardening() {
    std::lock_guard<std::mutex> lock(state_mutex);
    if (shutdown_attempted) return;
    shutdown_attempted = true;

#if defined(_WIN32)
    restore_windows_process_dacl();
#elif defined(__linux__)
    restore_linux_hardening();
#endif
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    apply_hardening();
    return JNI_VERSION_1_8;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*, void*) {
    shutdown_hardening();
}

extern "C" JNIEXPORT jint JNICALL
Java_combatant_client_runtime_nativeguard_NativeMemoryGuard_nativeApply(JNIEnv*, jclass) {
    apply_hardening();
    return applied_mask;
}

extern "C" JNIEXPORT jstring JNICALL
Java_combatant_client_runtime_nativeguard_NativeMemoryGuard_nativeLastError(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(state_mutex);
    return env->NewStringUTF(last_error.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_combatant_client_runtime_nativeguard_NativeMemoryGuard_nativeShutdown(JNIEnv*, jclass) {
    shutdown_hardening();
}
