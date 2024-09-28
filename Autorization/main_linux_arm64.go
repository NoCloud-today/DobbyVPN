package main

/*
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

int run_privileged_tool(const char* command) {
    FILE *pipe = popen(command, "r");
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
    "fmt"
    "log"
    "os"
    "path/filepath"
    "unsafe"
)

func main() {
    toolPath := "./DobbyVPN_ARM64/usr/local/bin/Cloak"
    
    absPath, err := filepath.Abs(toolPath)
    if err != nil {
        log.Fatal(err)
    }

    display := os.Getenv("DISPLAY")
    xauthority := os.Getenv("XAUTHORITY")
    command := fmt.Sprintf("pkexec env DISPLAY=%s XAUTHORITY=%s %s", display, xauthority, absPath)

    if err := runPrivilegedTool(command); err != nil {
        log.Fatal("Failed to run privileged tool:", err)
    }
}

func runPrivilegedTool(command string) error {
    cCommand := C.CString(command)
    defer C.free(unsafe.Pointer(cCommand))

    result := C.run_privileged_tool(cCommand)
    if result != 0 {
        return fmt.Errorf("permission denied")
    }
    return nil
}