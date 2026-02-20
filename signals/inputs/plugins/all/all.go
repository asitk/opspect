package all

// importing the plugins
import (
	_ "opspect/signals/inputs/plugins/kafka"
	_ "opspect/signals/inputs/plugins/logs"
	_ "opspect/signals/inputs/plugins/memcached"
	_ "opspect/signals/inputs/plugins/nwgraph"
	_ "opspect/signals/inputs/plugins/procstat"
	_ "opspect/signals/inputs/plugins/system"
)
