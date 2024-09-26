// +build linux

package main

import (
	"errors"
	"fmt"
        "os/user"
        "os/exec"
        "os"

	"github.com/Jigsaw-Code/outline-sdk/network"
	"github.com/songgao/water"
	"github.com/vishvananda/netlink"
)

func checkRoot() bool {
	user, err := user.Current()
	if err != nil {
		Logging.Info.Printf("Failed to get current user")
		return false
	}
	return user.Uid == "0"
}

type tunDevice struct {
	*water.Interface
	link netlink.Link
}

var _ network.IPDevice = (*tunDevice)(nil)

func newTunDevice(name, ip string) (d network.IPDevice, err error) {
        //if !checkRoot() {
	//	return nil, errors.New("this operation requires superuser privileges. Please run the program with sudo or as root")
	//}

	if len(name) == 0 {
		return nil, errors.New("name is required for TUN/TAP device")
	}
	if len(ip) == 0 {
		return nil, errors.New("ip is required for TUN/TAP device")
	}

	tun, err := water.New(water.Config{
		DeviceType: water.TUN,
		PlatformSpecificParams: water.PlatformSpecificParams{
			Name:    name,
			Persist: false,
		},
	})
	if err != nil {
		//return nil, fmt.Errorf("failed to create TUN/TAP device: %w", err)
                cmd := exec.Command("pkexec", os.Args[0])
                cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		cmd.Stdin = os.Stdin
		err := cmd.Run()
		if err != nil {
			fmt.Printf("Error: %s\n", err)
		}
	}

	defer func() {
		if err != nil {
			tun.Close()
		}
	}()

	tunLink, err := netlink.LinkByName(name)
	if err != nil {
		return nil, fmt.Errorf("newly created TUN/TAP device '%s' not found: %w", name, err)
	}

        Logging.Info.Printf("Outline/Tun: create tunLink : %v", tunLink)

	tunDev := &tunDevice{tun, tunLink}
	if err := tunDev.configureSubnet(ip); err != nil {
		return nil, fmt.Errorf("failed to configure TUN/TAP device subnet: %w", err)
	}
	if err := tunDev.bringUp(); err != nil {
		return nil, fmt.Errorf("failed to bring up TUN/TAP device: %w", err)
	}
        Logging.Info.Printf("Outline/Tun: tunDevice created")
	return tunDev, nil
}

func (d *tunDevice) MTU() int {
	return 1500
}

func (d *tunDevice) configureSubnet(ip string) error {
	subnet := ip + "/32"
	addr, err := netlink.ParseAddr(subnet)
	if err != nil {
		return fmt.Errorf("subnet address '%s' is not valid: %w", subnet, err)
	}
	if err := netlink.AddrAdd(d.link, addr); err != nil {
		return fmt.Errorf("failed to add subnet to TUN/TAP device '%s': %w", d.Interface.Name(), err)
	}
	return nil
}

func (d *tunDevice) bringUp() error {
	if err := netlink.LinkSetUp(d.link); err != nil {
		return fmt.Errorf("failed to bring TUN/TAP device '%s' up: %w", d.Interface.Name(), err)
	}
	return nil
}
