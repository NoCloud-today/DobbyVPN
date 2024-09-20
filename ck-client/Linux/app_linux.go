// +build linux

package main

import (
        "context"
	"fmt"
	//"io"
	"sync"
        "log"
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

        err = setSystemDNSServer(app.RoutingConfig.DNSServerIP)
	if err != nil {
		return fmt.Errorf("failed to configure system DNS: %w", err)
	}
	defer restoreSystemDNSServer()

	if err := startRouting(ss.GetServerIP().String(), app.RoutingConfig); err != nil {
		return fmt.Errorf("failed to configure routing: %w", err)
	}
	defer stopRouting(app.RoutingConfig.RoutingTableID)

        trafficCopyWg.Add(2)
    go func() {
        defer trafficCopyWg.Done()
        buffer := make([]byte, 65536)
        
        for {
            select {
            case <-ctx.Done():
                return
            default:
                n, err := tun.Read(buffer)
                if err != nil {
                    //fmt.Printf("Error reading from device: %x %v\n", n, err)
                    break
                }
                if n > 0 {
                    //log.Printf("Read %d bytes from tun\n", n)
                    //log.Printf("Data from tun: % x\n", buffer[:n])
                    
                    _, err = ss.Write(buffer[:n])
                    if err != nil {
                        //   log.Printf("Error writing to device: %v", err)
                        break
                    }
                }
            }
        }
    }()

    go func() {
        defer trafficCopyWg.Done()
        buf := make([]byte, 65536)
        for {
            select {
            case <-ctx.Done():
                return
            default:
                n, err := ss.Read(buf)
                if err != nil {
                //  fmt.Printf("Error reading from device: %v\n", err)
                    break
                }
                if n > 0 {
                  //log.Printf("Read %d bytes from OutlineDevice\n", n)
                  //log.Printf("Data from OutlineDevice: % x\n", buf[:n])

                    _, err = tun.Write(buf[:n])
                    if err != nil {
                    //    log.Printf("Error writing to tun: %v", err)
                        break
                    }
                }
            
            }
        }
        log.Printf("OutlineDevice -> tun stopped")
    }()

	// Copy the traffic from tun device to OutlineDevice bidirectionally
	//trafficCopyWg.Add(2)
	//go func() {
	//	defer trafficCopyWg.Done()
        //        select {
        //        case <-ctx.Done():
        //            return
        //        default:
	//	    written, err := io.Copy(ss, tun)
	//	    logging.Info.Printf("tun -> OutlineDevice stopped: %v %v\n", written, err)
        //        }
	//}()
	//go func() {
	//	defer trafficCopyWg.Done()
        //        select {
        //        case <-ctx.Done():
        //            return
        //        default:
	//	    written, err := io.Copy(tun, ss)
	//	    logging.Info.Printf("OutlineDevice -> tun stopped: %v %v\n", written, err)
        //        }
	//}()

	trafficCopyWg.Wait()

    
        trafficCopyWg.Wait()
        
        log.Printf("Disconnected")

        tun.Close()
        log.Printf("tun closed")
        ss.Close()
        log.Printf("device closed")        
        restoreSystemDNSServer()
        log.Printf("Stop routing")
        stopRouting(app.RoutingConfig.RoutingTableID)
        log.Printf("Stopped")
	return nil
}
