// +build android

package main

import (
        "context"
	"sync"
)

func (app App) Run(ctx context.Context) error {        
        trafficCopyWg := &sync.WaitGroup{}
	defer trafficCopyWg.Wait()

        trafficCopyWg.Add(2)
	go func() {
		defer trafficCopyWg.Done()
                select {
                case <-ctx.Done():
                    return
                default:
                }
	}()
	go func() {
		defer trafficCopyWg.Done()
                select {
                case <-ctx.Done():
                    return
                default:
                }
	}()

        trafficCopyWg.Wait()

    
        trafficCopyWg.Wait()

        return nil

}