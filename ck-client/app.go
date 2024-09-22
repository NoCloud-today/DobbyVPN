package outline
type App struct {
	TransportConfig *string
	RoutingConfig   *RoutingConfig
}

type RoutingConfig struct {
	TunDeviceName        string
	TunDeviceIP          string
	TunDeviceMTU         int
	TunGatewayCIDR       string
	RoutingTableID       int
	RoutingTablePriority int
	DNSServerIP          string
}
