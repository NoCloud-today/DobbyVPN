package main

/*
#include <stdlib.h>
#include <stdio.h>

int run_privileged_tool(const char* toolPath) {
    FILE *pipe = NULL;
    char command[256];
    snprintf(command, sizeof(command), "pkexec %s", toolPath);

    pipe = popen(command, "r");
    if (!pipe) {
        return -1;
    }

    char buffer[128];
    while (fgets(buffer, sizeof(buffer), pipe) != NULL) {
        printf("%s", buffer);
    }

    pclose(pipe);
    return 0;
}
*/
import "C"
import (
    "log"
    "unsafe"
    "os"
)

func main() {
    toolPath := "./libs/main"

    cToolPath := C.CString(toolPath)
    defer C.free(unsafe.Pointer(cToolPath))

    if err := runPrivilegedTool(cToolPath); err != nil {
        log.Fatal("Failed to run privileged tool:", err)
    }
}

func runPrivilegedTool(toolPath *C.char) error {
    result := C.run_privileged_tool(toolPath)
    if result != 0 {
        return os.ErrPermission
    }
    return nil
}
