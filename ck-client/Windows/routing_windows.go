//go:build windows
// +build windows

package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
)

var ipv4Subnets = []string{
	"0.0.0.0/1",
	"128.0.0.0/1",
}

var ipv4ReservedSubnets = []string{
	"0.0.0.0/8",
	"10.0.0.0/8",
	"100.64.0.0/10",
	"169.254.0.0/16",
	"172.16.0.0/12",
	"192.0.0.0/24",
	"192.0.2.0/24",
	"192.31.196.0/24",
	"192.52.193.0/24",
	"192.88.99.0/24",
	"192.168.0.0/16",
	"192.175.48.0/24",
	"198.18.0.0/15",
	"198.51.100.0/24",
	"203.0.113.0/24",
	"240.0.0.0/4",
}

const amneziawgSystemConfigPath = "C:\\ProgramData\\Amnezia\\AmneziaWG"

func executeCommand(command string) (string, error) {
	cmd := exec.Command("cmd", "/C", command)

	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow: true,
	}

	output, err := cmd.CombinedOutput()
	if err != nil {
		return string(output), fmt.Errorf("command execution failed: %w, output: %s", err, output)
	}
	Logging.Info.Printf("Outline/routing: Command executed: %s, output: %s", command, output)
	return string(output), nil
}

func saveWireguardConf(config string, fileName string) error {
	systemConfigPath := filepath.Join(amneziawgSystemConfigPath, fileName+".conf")

	err := os.MkdirAll(amneziawgSystemConfigPath, os.ModePerm)
	if err != nil {
		Logging.Info.Printf("failed to create directory %s: %w", amneziawgSystemConfigPath, err)
	}

	err = os.WriteFile(systemConfigPath, []byte(config), 0644)
	if err != nil {
		Logging.Info.Printf("failed to save AmneziaWG configuration to %s: %w", systemConfigPath, err)
	}

	Logging.Info.Printf("Configuration saved successfully to %s\n", systemConfigPath)
	return nil
}

func StartTunnel(name string) {
	systemConfigPath := filepath.Join(amneziawgSystemConfigPath, name+".conf")
	err := installTunnel(systemConfigPath)
	if err != nil {
		Logging.Info.Printf("Failed to start tunnel: %v", err)
	} else {
		Logging.Info.Printf("Tunnel started successfully: %s", name)
	}
}

func StopTunnel(name string) {
	err := uninstallTunnel(name)
	if err != nil {
		Logging.Info.Printf("Failed to stop tunnel: %v", err)
	} else {
		Logging.Info.Printf("Tunnel stopped successfully: %s", name)
	}
}

func CheckAndInstallWireGuard() error {
	Logging.Info.Printf("Checking AmneziaWG")

	_, err := exec.LookPath(TUNNEL_SERVICE_LIB_PATH)
	if err != nil {
		Logging.Info.Printf("AmneziaWG tunnel service not found at the path: %s", TUNNEL_SERVICE_LIB_PATH)

		return fmt.Errorf("AmneziaWG tunnel service not found")
	} else {
		Logging.Info.Printf("AmneziaWG is prepared for use")

		return nil
	}
}

func startRouting(proxyIP string, GatewayIP string, TunDeviceName string, MacAddress string, InterfaceName string, TunGateway string, TunDeviceIP string, addr []byte) error {
	Logging.Info.Printf("Outline/routing: Starting routing configuration for Windows...")
	Logging.Info.Printf("Outline/routing: Proxy IP: %s, Tun Device Name: %s, Tun Gateway: %s, Tun Device IP: %s, Gateway IP: %s, Mac Address: %s, Interface Name: %s",
		proxyIP, TunDeviceName, TunGateway, TunDeviceIP, GatewayIP, MacAddress, InterfaceName)
	Logging.Info.Printf("Outline/routing: Setting up IP rule...")
	addOrUpdateProxyRoute(proxyIP, GatewayIP, InterfaceName)
	Logging.Info.Printf("Outline/routing: Added IP proxy rules via table\n")
	addOrUpdateReservedSubnetBypass(GatewayIP, InterfaceName)
	Logging.Info.Printf("Outline/routing: Added IP reserved rules via table\n")
	addIpv4TapRedirect(TunGateway, TunDeviceName)
	Logging.Info.Printf("Outline/routing: Added IP rules via table\n")

	Logging.Info.Printf("Outline/routing: Routing configuration completed successfully.")

	err := AddNeighbor(TunDeviceName, TunGateway, formatMACAddress(addr))
	if err != nil {
		fmt.Println("Error:", err)
	}
	return nil
}

func stopRouting(proxyIp string, TunDeviceName string) {
	Logging.Info.Printf("Outline/routing: Cleaning up routing table and rules...")
	deleteProxyRoute(proxyIp)
	removeReservedSubnetBypass()
	stopRoutingIpv4(TunDeviceName)
	Logging.Info.Printf("Outline/routing: Cleaned up routing table and rules.")
}

func addOrUpdateProxyRoute(proxyIp string, gatewayIp string, gatewayInterfaceIndex string) {
	command := fmt.Sprintf("route change %s %s if \"%s\"", proxyIp, gatewayIp, gatewayInterfaceIndex)
	_, err := executeCommand(command)
	if err != nil {
		netshCommand := fmt.Sprintf("netsh interface ipv4 add route %s/32 nexthop=%s interface=\"%s\" metric=0 store=active",
			proxyIp, gatewayIp, gatewayInterfaceIndex)
		_, err = executeCommand(netshCommand)
		if err != nil {
			Logging.Info.Printf("Outline/routing: Failed to add or update proxy route for IP %s: %v\n", proxyIp, err)
		}
	}
}

func deleteProxyRoute(proxyIp string) {
	command := fmt.Sprintf("route delete %s", proxyIp)
	if proxyIp != "127.0.0.1" {
		_, err := executeCommand(command)
		if err != nil {
			Logging.Info.Printf("Outline/routing: Failed to delete proxy route for IP %s: %v\n", proxyIp, err)
		}
	}
}

func addOrUpdateReservedSubnetBypass(gatewayIp string, gatewayInterfaceIndex string) {
	for _, subnet := range ipv4ReservedSubnets {
		command := fmt.Sprintf("route change %s %s if \"%s\"", subnet, gatewayIp, gatewayInterfaceIndex)
		_, err := executeCommand(command)
		if err != nil {
			netshCommand := fmt.Sprintf("netsh interface ipv4 add route %s nexthop=%s interface=\"%s\" metric=0 store=active",
				subnet, gatewayIp, gatewayInterfaceIndex)
			_, err = executeCommand(netshCommand)
			if err != nil {
				Logging.Info.Printf("Outline/routing: Failed to add or update route for subnet %s: %v\n", subnet, err)
			}
		}
	}
}

func removeReservedSubnetBypass() {
	for _, subnet := range ipv4ReservedSubnets {
		command := fmt.Sprintf("route delete %s", subnet)
		_, err := executeCommand(command)
		if err != nil {
			Logging.Info.Printf("Outline/routing: Failed to delete route for subnet %s: %v\n", subnet, err)
		}
	}
}

func addIpv4TapRedirect(tapGatewayIP string, tapDeviceName string) {
	for _, subnet := range ipv4Subnets {
		command := fmt.Sprintf("netsh interface ipv4 add route %s nexthop=%s interface=\"%s\" metric=0 store=active",
			subnet, tapGatewayIP, tapDeviceName)
		_, err := executeCommand(command)
		if err != nil {
			setCommand := fmt.Sprintf("netsh interface ipv4 set route %s nexthop=%s interface=\"%s\" metric=0 store=active",
				subnet, tapGatewayIP, tapDeviceName)
			_, err = executeCommand(setCommand)
			if err != nil {
				Logging.Info.Printf("Outline/routing: Failed to add or set route for subnet %s: %v\n", subnet, err)
			}
		}
	}
}

func stopRoutingIpv4(loopbackInterfaceIndex string) {
	for _, subnet := range ipv4Subnets {
		command := fmt.Sprintf("netsh interface ipv4 add route %s interface=\"%s\" metric=0 store=active", subnet, loopbackInterfaceIndex)
		_, err := executeCommand(command)
		if err != nil {
			setCommand := fmt.Sprintf("netsh interface ipv4 set route %s interface=\"%s\" metric=0 store=active", subnet, loopbackInterfaceIndex)
			_, err = executeCommand(setCommand)
			if err != nil {
				Logging.Info.Printf("Outline/routing: Failed to add or set route for subnet %s: %v\n", subnet, err)
			}
		}
	}
}

func formatMACAddress(mac []byte) string {
	return strings.ToUpper(fmt.Sprintf("%02X-%02X-%02X-%02X-%02X-%02X", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]))
}

func AddNeighbor(interfaceName, gatewayIP, macAddress string) error {
	netshCommand := fmt.Sprintf(
		`netsh interface ip add neighbors "%s" "%s" "%s"`,
		interfaceName, gatewayIP, macAddress,
	)

	cmd := exec.Command("cmd", "/C", netshCommand)
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow: true,
	}
	output, err := cmd.CombinedOutput()
	if err == nil {
		fmt.Printf("Command arp executed  successfully: %s\n", string(output))
	}
	return nil
}
