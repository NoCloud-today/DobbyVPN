//go:build darwin

package main

import (
        "fmt"
	"os/exec"
)


func executeCommand(command string) (string, error) {
	cmd := exec.Command("bash", "-c", command)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return string(output), fmt.Errorf("command execution failed: %w, output: %s", err, output)
	}
	Logging.Info.Printf("Outline/routing: Command executed: %s, output: %s", command, output)
	return string(output), nil
}

func startRouting(proxyIP string, gatewayIP string, tunName string) error {
	removeOldDefaultRoute := fmt.Sprintf("sudo route delete default")
	if _, err := executeCommand(removeOldDefaultRoute); err != nil {
		Logging.Info.Printf("failed to remove old default route: %w", err)
	}

	addNewDefaultRoute := fmt.Sprintf("sudo route add default -interface %s", tunName)
	if _, err := executeCommand(addNewDefaultRoute); err != nil {
		Logging.Info.Printf("failed to add new default route: %w", err)
	}

	addSpecificRoute := fmt.Sprintf("sudo route add -net %s/32 %s", proxyIP, gatewayIP)
	if _, err := executeCommand(addSpecificRoute); err != nil {
		Logging.Info.Printf("failed to add specific route: %w", err)
	}

	return nil
}

func stopRouting(proxyIP string, gatewayIP string) error {
	removeNewDefaultRoute := fmt.Sprintf("sudo route delete default")
	if _, err := executeCommand(removeNewDefaultRoute); err != nil {
		Logging.Info.Printf("failed to remove new default route: %w", err)
	}

	addOldDefaultRoute := fmt.Sprintf("sudo route add default %s", gatewayIP)
	if _, err := executeCommand(addOldDefaultRoute); err != nil {
		Logging.Info.Printf("failed to add old default route: %w", err)
	}

	removeSpecificRoute := fmt.Sprintf("sudo route delete %s", proxyIP)
	if _, err := executeCommand(removeSpecificRoute); err != nil {
		Logging.Info.Printf("failed to remove specific route: %w", err)
	}

	return nil
}