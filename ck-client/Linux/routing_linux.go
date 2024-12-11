//go:build linux
// +build linux

package main

import (
	"errors"
	"os"
	"fmt"
	"io/ioutil"
	"net"
	"strings"
	"os/exec"
	"path/filepath"

	"github.com/vishvananda/netlink"

	"github.com/amnezia-vpn/amneziawg-go/conn"
	"github.com/amnezia-vpn/amneziawg-go/device"
	"github.com/amnezia-vpn/amneziawg-go/ipc"
	"github.com/amnezia-vpn/amneziawg-go/tun"
	"golang.org/x/sys/unix"

	sysctl "github.com/lorenzosaino/go-sysctl"
)

const amneziawgSystemConfigPath = "/etc/amnezia/amneziawg/"
var amneziawgDevice *device.Device = nil
var amneziawgUAPISocket net.Listener = nil

func saveWireguardConf(config string, fileName string) error {
	systemConfigPath := filepath.Join(amneziawgSystemConfigPath, fileName+".conf")
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

func configFromPath(configPath string, interfaceName string) (*Config, error) {
	configData, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}

	return FromWgQuickWithUnknownEncoding(string(configData), interfaceName)
}

func (config *Config) configureInterface() error {
	uapiString, err := config.ToUAPI()
	if err != nil {
		return err
	}

	reader := strings.NewReader(uapiString)

	return amneziawgDevice.IpcSetOperation(reader)
}

func (config *Config) setUpInterface() error {
	link, err := netlink.LinkByName(config.Name)
	if err != nil {
		return err
	}

	return netlink.LinkSetUp(link)
}

func (config *Config) addAddress(address string) error {
	// sudo ip -4 address add <address> dev <interfaceName>
	link, err := netlink.LinkByName(config.Name)
	if err != nil {
		return err
	}

	addr, err := netlink.ParseAddr(address)
	if err != nil {
		return err
	}

	return netlink.AddrAdd(link, addr)
}

func (config *Config) addRoute(address string) error {
	// sudo ip rule add not fwmark <table> table <table>
	ruleNot := netlink.NewRule()
	ruleNot.Invert = true
	ruleNot.Mark = uint32(config.Interface.FwMark)
	ruleNot.Table = int(config.Interface.FwMark)
	if err := netlink.RuleAdd(ruleNot); err != nil {
		return err
	}

	// sudo ip rule add table main suppress_prefixlength 0
	ruleAdd := netlink.NewRule()
	ruleAdd.Table = unix.RT_TABLE_MAIN
	ruleAdd.SuppressPrefixlen = 0
	if err := netlink.RuleAdd(ruleAdd); err != nil {
		return err
	}

	// sudo ip route add <address> dev <interfaceName> table <table>
	link, err := netlink.LinkByName(config.Name)
	if err != nil {
		return err
	}

	_, dst, err := net.ParseCIDR(address)
	if err != nil {
		return err
	}

	route := netlink.Route{LinkIndex: link.Attrs().Index, Dst: dst, Table: 51820}

	if err := netlink.RouteAdd(&route); err != nil {
		return err
	}

	// sudo sysctl -q net.ipv4.conf.all.src_valid_mark=1
	if err := sysctl.Set("net.ipv4.conf.all.src_valid_mark", "1"); err != nil {
		return err
	}

	return nil
}

func runInterface(configPath string, interfaceName string) error {
	// open TUN device (or use supplied fd)
	tdev, err := tun.CreateTUN(interfaceName, 1360)

	Logging.Info.Printf("Starting amneziawg interface")

	if err == nil {
		realInterfaceName, err2 := tdev.Name()
		if err2 == nil {
			interfaceName = realInterfaceName
		}
	} else {
		return err
	}

	// open UAPI file
	fileUAPI, err := ipc.UAPIOpen(interfaceName)

	if err != nil {
		return err
	}

	// Start device:
	// Logger definition:
	logLevel := device.LogLevelVerbose
	logger := device.NewLogger(logLevel, fmt.Sprintf("(%s) ", interfaceName))
	device := device.NewDevice(tdev, conn.NewDefaultBind(), logger)

	errs := make(chan error)
	uapi, err := ipc.UAPIListen(interfaceName, fileUAPI)

	if err != nil {
		return err
	}

	go func() {
		// Extra ipc configuration:
		for {
			conn, err := uapi.Accept()
			if err != nil {
				errs <- err
				return
			}
			go device.IpcHandle(conn)
		}
	}()

	amneziawgDevice = device
	amneziawgUAPISocket = uapi

	return nil
}

func StartTunnel(name string) {
	systemConfigPath := filepath.Join(amneziawgSystemConfigPath, name+".conf")

	err := runInterface(systemConfigPath, name)
	if err != nil {
		Logging.Info.Printf("Failed interface launch: %s", err)
		return
	} else {
		Logging.Info.Printf("Interface is already launched")
	}

	// Configure AmneziaWG
	config, err := configFromPath(systemConfigPath, name)

	if err != nil {
		Logging.Info.Printf("Failed to configure the tunnel")
		return
	} else {
		Logging.Info.Printf("Successful config parse")
	}

	if err := config.configureInterface(); err != nil {
		Logging.Info.Printf("Failed to configure the tunnel")
		return
	} else {
		Logging.Info.Printf("Config set success")
	}

	if config.setUpInterface(); err != nil {
		Logging.Info.Printf("Failed to set up the interface")
		return
	} else {
		Logging.Info.Printf("Interface set up success")
	}

	for _, address := range config.Interface.Addresses {
		if err := config.addAddress(address.String()); err != nil {
			Logging.Info.Printf("Failed to add the address %s", address.String())
			return
		} else {
			Logging.Info.Printf("Address %s addition success\n", address.String())
		}
	}

	for _, peer := range config.Peers {
		for _, allowed_ip := range peer.AllowedIPs {
			if err := config.addRoute(allowed_ip.String()); err != nil {
				Logging.Info.Printf("Route from %s address to %s is failed", allowed_ip.String(), config.Name)
				return
			} else {
				Logging.Info.Printf("Route from %s address to %s is successful", allowed_ip.String(), config.Name)
			}
		}
	}
}

func StopTunnel(name string) {
	link, err := netlink.LinkByName(name)
	if err != nil {
		err = netlink.LinkDel(link)
	}

	if err != nil {
		Logging.Info.Printf("Tunnel is already stopped")
	} else {
		Logging.Info.Printf("Stop tunnel: %s", name)
	}

	if err := amneziawgUAPISocket.Close(); err != nil {
		Logging.Info.Printf("UAPI socket close failure")
	} else {
		Logging.Info.Printf("UAPI socket closed")
	}
	
	amneziawgDevice.Close()
	Logging.Info.Printf("Interface closed")
}

func CheckAndInstallWireGuard() error {
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
