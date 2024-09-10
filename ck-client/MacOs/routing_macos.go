//go:build darwin

package main

import (
	"log"
)

func startRouting(proxyIP string, config *RoutingConfig) error {
	log.Printf("Start routing")
	return nil
}

func stopRouting(routingTable int) {
	log.Printf("Stop routing")
}
