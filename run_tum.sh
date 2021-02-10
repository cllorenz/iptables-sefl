#!/bin/bash

echo 'run --iptables data/tum/iptables.out --routing_table data/empty-routing-table --ips data/empty-ips --input_port eth0 --destination_port 80' | sbt
