// This shows how a received messages may be written to the JMeter log:
def baos = new java.io.ByteArrayOutputStream()
message.dump(new java.io.PrintStream(baos), '')
log.info("Mock received: $baos")

message.setResponseMTI()
message.set(39, '00')
source.send(message)
