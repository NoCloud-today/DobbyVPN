// +build linux

package main

import (
        "context"
	"fmt"
	"io"
	"sync"
)

func (app App) Run(ctx context.Context) error {
	// this WaitGroup must Wait() after tun is closed
	trafficCopyWg := &sync.WaitGroup{}
	defer trafficCopyWg.Wait()

	tun, err := newTunDevice(app.RoutingConfig.TunDeviceName, app.RoutingConfig.TunDeviceIP)
	if err != nil {
		return fmt.Errorf("failed to create tun device: %w", err)
	}
	defer tun.Close()

	// disable IPv6 before resolving Shadowsocks server IP
	prevIPv6, err := enableIPv6(false)
	if err != nil {
		return fmt.Errorf("failed to disable IPv6: %w", err)
	}
	defer enableIPv6(prevIPv6)

	ss, err := NewOutlineDevice(*app.TransportConfig)
	if err != nil {
		return fmt.Errorf("failed to create OutlineDevice: %w", err)
	}
	defer ss.Close()

	ss.Refresh()

	// Copy the traffic from tun device to OutlineDevice bidirectionally
	trafficCopyWg.Add(2)
	go func() {
		defer trafficCopyWg.Done()
                select {
                case <-ctx.Done():
                    return
                default:
		    written, err := io.Copy(ss, tun)
		    logging.Info.Printf("tun -> OutlineDevice stopped: %v %v\n", written, err)
                }
	}()
	go func() {
		defer trafficCopyWg.Done()
                select {
                case <-ctx.Done():
                    return
                default:
		    written, err := io.Copy(tun, ss)
		    logging.Info.Printf("OutlineDevice -> tun stopped: %v %v\n", written, err)
                }
	}()

	err = setSystemDNSServer(app.RoutingConfig.DNSServerIP)
	if err != nil {
		return fmt.Errorf("failed to configure system DNS: %w", err)
	}
	defer restoreSystemDNSServer()

	if err := startRouting(ss.GetServerIP().String(), app.RoutingConfig); err != nil {
		return fmt.Errorf("failed to configure routing: %w", err)
	}
	defer stopRouting(app.RoutingConfig.RoutingTableID)

	trafficCopyWg.Wait()

    
        trafficCopyWg.Wait()
    
        tun.Close()
        ss.Close()
	return nil
}
