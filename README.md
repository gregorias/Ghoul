Overview
========

Ghoul is a secure DHT implementation which uses Kademlia DHT protocol at its
basis. By secure I mean that it actively prevents common attacks on DHT systems
such as sybil or eclipse attacks and has self-recovery properties. It does so by
using a small set of trusted registration authorities, which are generally
trusted, but not completely, for key generation and active malicious node
detection.
