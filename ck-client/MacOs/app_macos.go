//go:build darwin

package main

import (
	"context"
	"fmt"
	"log"
	"os/exec"
	"sync"
	"time"
	"io"
)

func (app App) Run(ctx context.Context) error {
	// this WaitGroup must Wait() after tun is closed
	trafficCopyWg := &sync.WaitGroup{}
	defer trafficCopyWg.Wait()

        //if !checkRoot() {
	//	return errors.New("this operation requires superuser privileges. Please run the program with sudo or as root")
	//}

	tun, err := newTunDevice(app.RoutingConfig.TunDeviceName, app.RoutingConfig.TunDeviceIP)
	if err != nil {
		return fmt.Errorf("failed to create tun device: %w, open app with sudo", err)
	}
	defer tun.Close()

        Logging.Info.Printf("Tun created")

	ss, err := NewOutlineDevice(*app.TransportConfig)
	if err != nil {
		return fmt.Errorf("failed to create OutlineDevice: %w", err)
	}
	defer ss.Close()

	ss.Refresh()

        Logging.Info.Printf("Device created")

	// Copy the traffic from tun device to OutlineDevice bidirectionally
	trafficCopyWg.Add(2)
	go func() {
		defer trafficCopyWg.Done()
                select {
                case <-ctx.Done():
                    return
                default:
		    written, err := io.Copy(ss, tun)
		    log.Printf("tun -> OutlineDevice stopped: %v %v\n", written, err)
                }
	}()
	go func() {
		defer trafficCopyWg.Done()
                select {
                case <-ctx.Done():
                    return
                default:
		    written, err := io.Copy(tun, ss)
		    Logging.Info.Printf("OutlineDevice -> tun stopped: %v %v\n", written, err)
                }
	}()
	
	go func() {
	    for {
		select {
		case <-ctx.Done():
			return
		default:
			cmd := exec.Command("netstat", "-rn")
			output, err := cmd.Output()
			if err != nil {
				Logging.Info.Printf("failed to execute netstat")
			} else {
				Logging.Info.Printf("Routing table:\n%s", string(output))
			}
			time.Sleep(6 * time.Second)
		}
	    }
	}()


	if err := startRouting(ss.GetServerIP().String(), app.RoutingConfig); err != nil {
		return fmt.Errorf("failed to configure routing: %w", err)
	}
	defer stopRouting(app.RoutingConfig.RoutingTableID)

	trafficCopyWg.Wait()

    
        trafficCopyWg.Wait()
    
        tun.Close()
        Logging.Info.Printf("Tun closed")
        ss.Close()
        Logging.Info.Printf("Device closed")
        Logging.Info.Printf("Stopped")
	return nil
}
