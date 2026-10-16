FROM cp.icr.io/cp/appc/ace:13.0.9.0-r1
COPY HelloWorld.bar HelloWorldWithMQ.bar /tmp/bars/
RUN bash -c ". /opt/ibm/ace-13/server/bin/mqsiprofile && printf 'CallHome:\n  enabled: false\n' > \"\${MQSI_WORKPATH:-/var/mqsi}/registry.13.0.conf.yaml\" && ibmint deploy --input-bar-file /tmp/bars/HelloWorld.bar --output-work-directory /home/aceuser/ace-server && ibmint deploy --input-bar-file /tmp/bars/HelloWorldWithMQ.bar --output-work-directory /home/aceuser/ace-server"
COPY server.conf.yaml admin-mcp-tools.json /home/aceuser/ace-server/overrides/
EXPOSE 7650
