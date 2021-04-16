package com.rarible.ethereum.listener.log

import org.springframework.stereotype.Component

@Component
class LogEventDescriptorHolder(val list: List<LogEventDescriptor<*>>)