// +build linux

package main

import (
	"errors"
	"net"
        "fmt"
	"os/exec"
	"io/ioutil"
	"path/filepath"
	"github.com/vishvananda/netlink"
)

const wireguardSystemConfigPath = "/etc/wireguard/"

func saveWireguardConf(config string, fileName string) error {
	systemConfigPath := filepath.Join(wireguardSystemConfigPath, fileName+".conf")
	return ioutil.WriteFile(systemConfigPath, []byte(config), 0644)
}

func executeCommand(command string) (string, error) {
	cmd := exec.Command("bash", "-c", command)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return string(output), fmt.Errorf("command execution failed: %w, output: %s", err, output)
	}
	Logging.Info.Printf("Outline/routing: Command executed: %s, output: %s", command, output)
	return string(output), nil
}

func StartTunnel(name string) {
        systemConfigPath := filepath.Join(wireguardSystemConfigPath, fileName+".conf")

        cmd := exec.Command("sudo", "./libs/wireguard-go", name)
        err = cmd.Run()

	if err != nil {
		Logging.Info.Printf("Interface is already launched")
	} else {
		Logging.Info.Printf("Launch interface")
	}

        cmd = exec.Command("sudo", "wg", "setconf", name, systemConfigPath)
        err = cmd.Run()

	if err != nil {
		Logging.Info.Printf("Config is already launched")
	} else {
		Logging.Info.Printf("Launch config")
	}

        cmd = exec.Command("sudo", "ip", "address", "add", "10.66.66.2/32", "dev", name)
        err = cmd.Run()

	if err != nil {
		Logging.Info.Printf("Address is already launched")
	} else {
		Logging.Info.Printf("Launch address")
	}

        cmd = exec.Command("sudo", "ip", "link", "set", name, "up")
        err = cmd.Run()

	if err != nil {
		Logging.Info.Printf("Tunnel is already launched")
	} else {
		Logging.Info.Printf("Launch tunnel")
	}   

        cmd = exec.Command("sudo", "ip", "-4", "route", "add", "128.0.0.0/1", "dev", name)
        err = cmd.Run()

	if err != nil {
		Logging.Info.Printf("Address is already launched")
	} else {
		Logging.Info.Printf("Launch Address")
	}   

        cmd = exec.Command("sudo", "ip", "-4", "route", "add", "0.0.0.0/1", "dev", name)
        err = cmd.Run()

	if err != nil {
		Logging.Info.Printf("Address is already launched")
	} else {
		Logging.Info.Printf("Launch Address")
	}   
}

func StopTunnel(name string) {
        cmd := exec.Command("sudo", "ip", "link", "del", name)
        err := cmd.Run()

	if err != nil {
		Logging.Info.Printf("Tunnel is already stopped")
	} else {
		Logging.Info.Printf("Stop tunnel: %s", name)
	}
}

func CheckAndInstallWireGuard() error {
	cmd := exec.Command("sudo", "wg", "--version")
	err := cmd.Run()

	if err != nil {
		Logging.Info.Printf("WireGuard is not install")

		installCmd := exec.Command("sudo", "apt", "install", "-y", "wireguard-tools")
		installErr := installCmd.Run()

		if installErr != nil {
			Logging.Info.Printf("Error install WireGuard: %w", installErr)
		}

		Logging.Info.Printf("WireGuard is sucessfully installed.")
	} else {
		Logging.Info.Printf("WireGuard is already installed.")
	}

	return nil
}


func startRouting(proxyIP string, gatewayIP string, interfaceName string, tunIp string, tunName string) error {
	removeOldDefaultRoute := fmt.Sprintf("sudo ip route del default via %s dev %s", gatewayIP, interfaceName)
	if _, err := executeCommand(removeOldDefaultRoute); err != nil {
		Logging.Info.Printf("failed to remove old default route: %w", err)
	}

	addNewDefaultRoute := fmt.Sprintf("sudo ip route add default via %s dev %s metric 10", tunIp, tunName)
	if _, err := executeCommand(addNewDefaultRoute); err != nil {
		Logging.Info.Printf("failed to add new default route: %w", err)
	}

	addSpecificRoute := fmt.Sprintf("sudo ip route add %s via %s dev %s metric 5", proxyIP, gatewayIP, interfaceName)
	if _, err := executeCommand(addSpecificRoute); err != nil {
		Logging.Info.Printf("failed to add specific route: %w", err)
	}

	return nil
}

func stopRouting(proxyIP string, gatewayIP string, interfaceName string, tunIp string, tunName string) error {
	removeNewDefaultRoute := fmt.Sprintf("sudo ip route del default via %s dev %s", tunIp, tunName)
	if _, err := executeCommand(removeNewDefaultRoute); err != nil {
		Logging.Info.Printf("failed to remove new default route: %w", err)
	}

	addOldDefaultRoute := fmt.Sprintf("sudo ip route add default via %s dev %s metric 600", gatewayIP, interfaceName)
	if _, err := executeCommand(addOldDefaultRoute); err != nil {
		Logging.Info.Printf("failed to add old default route: %w", err)
	}

	removeSpecificRoute := fmt.Sprintf("sudo ip route del %s via %s dev %s", proxyIP, gatewayIP, interfaceName)
	if _, err := executeCommand(removeSpecificRoute); err != nil {
		Logging.Info.Printf("failed to remove specific route: %w", err)
	}

	return nil
}


var ipRule *netlink.Rule = nil

func setupRoutingTable(routingTable int, tunName, gwSubnet string, tunIP string) error {
	tun, err := netlink.LinkByName(tunName)
	if err != nil {
		return fmt.Errorf("failed to find tun device '%s': %w", tunName, err)
	}

	dst, err := netlink.ParseIPNet(gwSubnet)
	if err != nil {
		return fmt.Errorf("failed to parse gateway '%s': %w", gwSubnet, err)
	}

	r := netlink.Route{
		LinkIndex: tun.Attrs().Index,
		Table:     routingTable,
		Dst:       dst,
		Src:       net.ParseIP(tunIP),
		Scope:     netlink.SCOPE_LINK,
	}

	if err = netlink.RouteAdd(&r); err != nil {
		return fmt.Errorf("failed to add routing entry '%v' -> '%v': %w", r.Src, r.Dst, err)
	}
	Logging.Info.Printf("routing traffic from %v to %v through nic %v\n", r.Src, r.Dst, r.LinkIndex)

	r = netlink.Route{
		LinkIndex: tun.Attrs().Index,
		Table:     routingTable,
		Gw:        dst.IP,
	}

	if err := netlink.RouteAdd(&r); err != nil {
		return fmt.Errorf("failed to add gateway routing entry '%v': %w", r.Gw, err)
	}
	Logging.Info.Printf("routing traffic via gw %v through nic %v...\n", r.Gw, r.LinkIndex)

	return nil
}

func cleanUpRoutingTable(routingTable int) error {
	filter := netlink.Route{Table: routingTable}
	routes, err := netlink.RouteListFiltered(netlink.FAMILY_V4, &filter, netlink.RT_FILTER_TABLE)
	if err != nil {
		return fmt.Errorf("failed to list entries in routing table '%v': %w", routingTable, err)
	}

	var rtDelErr error = nil
	for _, route := range routes {
		if err := netlink.RouteDel(&route); err != nil {
			rtDelErr = errors.Join(rtDelErr, fmt.Errorf("failed to remove routing entry: %w", err))
		}
	}
	if rtDelErr == nil {
		Logging.Info.Printf("routing table '%v' has been cleaned up\n", routingTable)
	}
	return rtDelErr
}

func setupIpRule(svrIp string, routingTable, routingPriority int) error {
	dst, err := netlink.ParseIPNet(svrIp)
	if err != nil {
		return fmt.Errorf("failed to parse server IP CIDR '%s': %w", svrIp, err)
	}

	// todo: exclude server IP will cause issues when accessing services on the same server,
	//       use fwmask to protect the shadowsocks socket instead
	ipRule = netlink.NewRule()
	ipRule.Priority = routingPriority
	ipRule.Family = netlink.FAMILY_V4
	ipRule.Table = routingTable
	ipRule.Dst = dst
	ipRule.Invert = true

	if err := netlink.RuleAdd(ipRule); err != nil {
		return fmt.Errorf("failed to add IP rule (table %v, dst %v): %w", ipRule.Table, ipRule.Dst, err)
	}
	Logging.Info.Printf("ip rule 'from all not to %v via table %v' created\n", ipRule.Dst, ipRule.Table)
	return nil
}

func cleanUpRule() error {
	if ipRule == nil {
		return nil
	}
	if err := netlink.RuleDel(ipRule); err != nil {
		return fmt.Errorf("failed to delete IP rule of routing table '%v': %w", ipRule.Table, err)
	}
	Logging.Info.Printf("ip rule of routing table '%v' deleted\n", ipRule.Table)
	ipRule = nil
	return nil
}