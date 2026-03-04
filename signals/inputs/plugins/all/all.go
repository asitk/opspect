package all

// importing the plugins
import (
	_ "opscog/signals/inputs/plugins/kafka"
	_ "opscog/signals/inputs/plugins/logs"
	_ "opscog/signals/inputs/plugins/memcached"
	_ "opscog/signals/inputs/plugins/nwgraph"
	_ "opscog/signals/inputs/plugins/procstat"
	_ "opscog/signals/inputs/plugins/system"
)
