package main

/*
#cgo LDFLAGS: -framework Security
#include <Security/Security.h>
#include <stdlib.h>
#include <stdio.h>

int authorize_user(AuthorizationRef *authRef, const char* right) {
    OSStatus status;

    status = AuthorizationCreate(NULL, kAuthorizationEmptyEnvironment, kAuthorizationFlagDefaults, authRef);
    if (status != errAuthorizationSuccess) {
        return -1;
    }

    AuthorizationItem rightItem = {right, 0, NULL, 0};
    AuthorizationItemSet rights = {1, &rightItem};

    status = AuthorizationCopyRights(*authRef, &rights, NULL, 
        kAuthorizationFlagInteractionAllowed | 
        kAuthorizationFlagPreAuthorize | 
        kAuthorizationFlagExtendRights, NULL);

    if (status != errAuthorizationSuccess) {
        AuthorizationFree(*authRef, kAuthorizationFlagDefaults);
        return -1;
    }

    return 0;
}

int run_privileged_tool(const char* toolPath, AuthorizationRef authRef) {
    FILE *pipe = NULL;
    char *args[] = {NULL};

    OSStatus status = AuthorizationExecuteWithPrivileges(authRef, toolPath, kAuthorizationFlagDefaults, args, &pipe);
    if (status != errAuthorizationSuccess) {
        return -1;
    }

    if (pipe != NULL) {
        fclose(pipe);
    }

    AuthorizationFree(authRef, kAuthorizationFlagDefaults);
    
    return 0; 
}
*/
import "C"
import (
    "log"
    "os"
    "path/filepath"
    "unsafe"
)

func main() {
    var authRef C.AuthorizationRef
    if err := authorizeUser(&authRef); err != nil {
        log.Fatal("Authorization failed:", err)
        return
    }

    toolPath := "./libs/main"

    absPath, err := filepath.Abs(toolPath)
    if err != nil {
        log.Fatal(err)
    }

    cToolPath := C.CString(absPath)
    defer C.free(unsafe.Pointer(cToolPath))

    if err := runPrivilegedTool(cToolPath, authRef); err != nil {
        log.Fatal("Failed to run privileged tool:", err)
    }
}

func authorizeUser(authRef *C.AuthorizationRef) error {
    cRight := C.CString("com.apple.app-sandbox.set-attributes") 
    defer C.free(unsafe.Pointer(cRight))

    result := C.authorize_user(authRef, cRight)
    if result != 0 {
        return os.ErrPermission
    }
    return nil
}

func runPrivilegedTool(toolPath *C.char, authRef C.AuthorizationRef) error {
    result := C.run_privileged_tool(toolPath, authRef)
    if result != 0 {
        return os.ErrPermission
    }
    return nil
}