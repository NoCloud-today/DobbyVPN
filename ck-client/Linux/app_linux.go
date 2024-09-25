// +build linux

package main

import (
        "context"
	"fmt"
        "errors"
	//"io"
	"sync"
        "log"
)

func (app App) Run(ctx context.Context) error {
	// this WaitGroup must Wait() after tun is closed
	trafficCopyWg := &sync.WaitGroup{}
	defer trafficCopyWg.Wait()

        //if !checkRoot() {
	//	return errors.New("this operation requires superuser privileges. Please run the program with sudo or as root")
	//}

        Logging.Info.Printf("Outline/Run: Start creating tun")

	tun, err := newTunDevice(app.RoutingConfig.TunDeviceName, app.RoutingConfig.TunDeviceIP)
	if err != nil {
		return fmt.Errorf("failed to create tun device: %w", err)
	}
	defer tun.Close()

        Logging.Info.Printf("Outline/Run: Start device")

	ss, err := NewOutlineDevice(*app.TransportConfig)
	if err != nil {
		return fmt.Errorf("failed to create OutlineDevice: %w", err)
	}
	defer ss.Close()

	ss.Refresh()

        Logging.Info.Printf("Outline/Run: Start routing")

	if err := startRouting(ss.GetServerIP().String(), app.RoutingConfig); err != nil {
		return fmt.Errorf("failed to configure routing: %w", err)
	}
	defer stopRouting(app.RoutingConfig.RoutingTableID)

        Logging.Info.Printf("Outline/Run: Made table routing")

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
	//	    Logging.Info.Printf("tun -> OutlineDevice stopped: %v %v\n", written, err)
        //        }
	//}()
	//go func() {
	//	defer trafficCopyWg.Done()
        //        select {
        //        case <-ctx.Done():
        //            return
        //        default:
	//	    written, err := io.Copy(tun, ss)
	//	    Logging.Info.Printf("OutlineDevice -> tun stopped: %v %v\n", written, err)
        //        }
	//}()

	trafficCopyWg.Wait()

    
        trafficCopyWg.Wait()
        
        log.Printf("Outline/Run: Disconnected")

        tun.Close()
        log.Printf("Outline/Run: tun closed")
        ss.Close()
        log.Printf("Outline/Run: device closed")        
        log.Printf("Outline/Run: Stop routing")
        stopRouting(app.RoutingConfig.RoutingTableID)
        log.Printf("Outline/Run: Stopped")
	return nil
}
