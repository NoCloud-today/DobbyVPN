// +build linux

package main

import (
        "context"
	"fmt"
	//"io"
	"sync"
        "log"
	"bufio"
	"os/exec"
	"strings"

	"github.com/jackpal/gateway"
)

func add_route(proxyIp string) {
    gatewayIP, err := gateway.DiscoverGateway()
    if err != nil {
        panic(err)
    }
    interfaceName, err := FindInterfaceByGateway(gatewayIP.String())
    if err != nil {
        panic(err)
    }
    
    addSpecificRoute := fmt.Sprintf("sudo ip route add %s via %s dev %s metric 5", proxyIp, gatewayIP.String(), interfaceName)
    if _, err := executeCommand(addSpecificRoute); err != nil {
	Logging.Info.Printf("failed to add specific route: %w", err)
    }
}

func FindInterfaceByGateway(gatewayIP string) (string, error) {
    cmd := exec.Command("ip", "route")
    output, err := cmd.Output()
    if err != nil {
        return "", fmt.Errorf("fail to execute a command route print: %v", err)
    }

    scanner := bufio.NewScanner(strings.NewReader(string(output)))
    var foundGateway bool
    for scanner.Scan() {
        line := scanner.Text()
        if strings.Contains(line, gatewayIP) {
            foundGateway = true
            parts := strings.Fields(line)
            if len(parts) >= 5 {
                interfaceName := parts[4]
                return interfaceName, nil
            }
        }
    }

    if !foundGateway {
        return "", fmt.Errorf("gateway %s is not found in the table", gatewayIP)
    }

    return "", fmt.Errorf("no interface %s", gatewayIP)
}

func (app App) Run(ctx context.Context) error {
	// this WaitGroup must Wait() after tun is closed
	
	tunIp := "10.0.85.2"
	tunName:= "outline-tun0"
	
	gatewayIP, err := gateway.DiscoverGateway()
        if err != nil {
            panic(err)
        }
   
        interfaceName, err := FindInterfaceByGateway(gatewayIP.String())
        if err != nil {
            panic(err)
        }
        
	trafficCopyWg := &sync.WaitGroup{}
	defer trafficCopyWg.Wait()

        //if !checkRoot() {
	//	return errors.New("this operation requires superuser privileges. Please run the program with sudo or as root")
	//}

        Logging.Info.Printf("Outline/Run: Start creating tun")

	tun, err := newTunDevice(tunName, tunIp)
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

	if err := startRouting(ss.GetServerIP().String(), gatewayIP.String(), interfaceName, tunIp, tunName); err != nil {
		return fmt.Errorf("failed to configure routing: %w", err)
	}
	defer stopRouting(ss.GetServerIP().String(), gatewayIP.String(), interfaceName, tunIp, tunName)

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

	trafficCopyWg.Wait()

    
        trafficCopyWg.Wait()
        
        log.Printf("Outline/Run: Disconnected")

        tun.Close()
        log.Printf("Outline/Run: tun closed")
        ss.Close()
        log.Printf("Outline/Run: device closed")        
        log.Printf("Outline/Run: Stop routing")
        stopRouting(ss.GetServerIP().String(), gatewayIP.String(), interfaceName, tunIp, tunName)
        log.Printf("Outline/Run: Stopped")
	return nil
}