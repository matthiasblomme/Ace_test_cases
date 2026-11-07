# Demo 3 - connector actions as MCP tools, on OpenShift

Demo 3 is the one MCP flavour that cannot run on a laptop. IBM restricts Dashboard-created
MCP servers to Red Hat OpenShift and IBM Cloud Kubernetes Service, because the operator
needs to create routes or ingresses for integration runtimes automatically. The reasoning,
and the exact failure that shows up on plain Kubernetes, is in `../DEMO.md` under "Demo 3".

This folder is what turns a fresh OpenShift cluster into a recordable demo 3.

## Status of this document

Separating what is checked from what is not, because it matters when something does not
reconcile on the day:

| Claim | Standing |
|---|---|
| Platform restriction (OpenShift or IBM Cloud Kubernetes Service only) | Verified in the current product documentation |
| Operator channel `v13.6` gives operator 13.6.0 | Verified |
| Operator 13.6.0 supports operand `13.0.9.0-r1` | Verified |
| License id `L-RJGW-BUAA2R` for operators 13.4.0 to 13.6.0 | Verified. It replaced `L-KPRV-AUG9NC`, which is what the older minikube manifests carry |
| MCP server version floors (connectors 13.0.6.1-r1, REST API operations 13.0.8.0-r1) | Verified |
| Integration runtime resource defaults (1000m CPU, 768Mi at 13.0.7.0-r1 or later) | Verified |
| The wizard screens and the objects it creates | From the documentation, not yet seen on a live 13.0.9 Dashboard |
| The Dashboard CR applies cleanly as written | **Not verified.** Built from the documented fields plus the shape that worked on 13.0.5.0-r1. The fields most likely to have moved are the two `integrationKeycloak` blocks and `displayMode` |
| The TechZone catalogue still offers the environment named below | **Not verified.** Catalogue entries get renamed |

Before trusting the Dashboard CR, get the operator's own sample for the version actually
installed:

```bash
oc get csv -n openshift-operators -o jsonpath='{range .items[?(@.spec.displayName=="IBM App Connect")]}{.metadata.annotations.alm-examples}{end}'
```

That returns working example CRs straight from the installed operator, which beats any
document including this one. `oc explain dashboard.spec` settles individual fields.

## 1. Reserve the cluster

This is the one step that has to be done by hand, under your own IBM id.

1. Open the Automation Application Integration collection:
   `https://techzone.ibm.com/collection/automation-application-integration`
2. On the **Environments** tab, pick the OpenShift Cluster OCP-V entry (the vault prep
   guide calls it "Integration L4 Courses - OpenShift Cluster OCP-V (V2)", the
   `ocpv-cloud` environment). Confirm the current name on the page.
3. Business purpose **Education**. Opportunity fields can stay empty.
4. Pick a nearby region, then set the schedule. Leave room: provisioning takes 30 to 75
   minutes and you get an email per state change.
5. On the Configuration page, the smallest offered cluster is enough. A Dashboard plus one
   MCP integration runtime asks for roughly 1.2 CPU and 2 GB in requests. The 5 nodes with
   16 cores and 64 GB that the API Connect prep guide calls for is an API Connect
   requirement, not an ACE one. Choose an OpenShift version in the supported range (4.12,
   or 4.14 through 4.21); confirm the pairing for operand 13.0.9.0-r1 once the operator is
   installed.
6. Accept the terms and submit.

Two limits worth knowing before you plan the recording: TechZone allows **two** active
Education reservations in parallel, and a reservation can usually be extended twice.

When it is ready, the reservation details page carries the cluster admin username
(`kubeadmin`), its password, and the OpenShift console URL. None of those belong in this
repository.

## 2. Log in and create the project

```bash
oc login --username kubeadmin --server https://<api-url>:6443
oc new-project ace-mcp-demo
```

## 3. Entitlement key

The operand images come from `cp.icr.io` and need your IBM entitlement key, which you can
retrieve from `https://myibm.ibm.com/products-services/containerlibrary`. The catalog index
image in `olm/catalogsource.yaml` is public and does not need it.

```bash
oc create secret docker-registry ibm-entitlement-key \
  --docker-server=cp.icr.io \
  --docker-username=cp \
  --docker-password=<entitlement-key> \
  -n ace-mcp-demo
```

The key itself never goes in a file in this repository.

## 4. Install the operator

```bash
oc apply -f olm/catalogsource.yaml
oc apply -f olm/subscription.yaml
```

Wait for it to land, then confirm the version, because every floor in this demo is an
operator-version floor:

```bash
oc get csv -n openshift-operators | grep -i appconnect
```

Expect `ibm-appconnect.v13.6.0` with phase `Succeeded`.

## 5. Create the Dashboard

```bash
oc apply -f ace-dashboard-pvc.yaml
oc apply -f ace-dashboard.yaml

oc get dashboard ace-dashboard -n ace-mcp-demo -w
```

Wait for status `Ready` and a resolved version of `13.0.9.0-r1`. Then get the URL, which on
OpenShift the operator creates for you - that automatic route is the whole reason this
works here and not on minikube:

```bash
oc get route -n ace-mcp-demo
```

## 6. Create the connector MCP server

**Prerequisite that is easy to forget: you need a SaaS account you can authenticate on
camera.** The wizard does not finish without one. Slack is the cheap option - a new
workspace and app are free, and the OAuth handshake was already proven to work. Have it
ready before you start recording, and keep the credential entry off screen.

In the Dashboard:

1. Open the **Model Context Protocol (MCP) servers** page and click **Create MCP
   server**.
2. Integration runtime preferences: choose **New server (connectors based)**. This creates
   a fresh integration runtime preconfigured for MCP.
3. Server details: give the server a name. The integration runtime is created with the same
   name. On OpenShift you can pick any compatible version and license from the drop-downs.
   The runtime defaults to 1000m CPU and 768Mi memory, limits and requests both, at operand
   13.0.7.0-r1 or later.
4. Click **Create and proceed**. The Dashboard reports that it is creating the integration
   runtime and that deployment takes a few minutes. Four things appear:

   | Object | Name | Purpose |
   |---|---|---|
   | Integration runtime | `<serverName>` | Hosts the MCP server |
   | Configuration, type `Accounts` | `<serverName>-acc` | System-generated secret holding the connector account credentials |
   | Configuration, type `REST Admin SSL files` | `<serverName>-ir-adminssl` | Self-signed TLS certificate for Dashboard-to-MCP-server traffic. TLS is the default |
   | Configuration, type `setdbparms.txt` | `<serverName>-mcp-ba-creds` | Basic auth username and password for the MCP endpoint |

5. On the **MCP tools** step, pick the connector, authenticate the account, and select the
   actions that become tools.
6. When it is done, go to **Runtimes**, click the runtime tile, and open the **Model
   Context Protocol (MCP) tools** tab. That tab carries the endpoint details and the tool
   list.

## What to show on camera

The useful contrast with demos 1 and 2, which is the point of showing this at all:

- Demo 2 exposed an operation **you wrote** as a tool. Demo 3 exposes actions on a
  **third-party application** as tools, with no flow authored at all.
- One MCP server here is a whole integration runtime in its own pod, licensed like any
  other. That is a cost and sizing statement, not a detail.
- The endpoint is TLS with basic auth by default, and both come from configuration objects
  the wizard generated. Worth showing the Configuration page: it explains where the
  credentials live without reading any secret out loud.
- Say plainly that this needs OpenShift or IBM Cloud Kubernetes Service, and why: the
  operator has to create the route for the runtime itself.

## Bonus, if the cluster is up anyway

Operator 13.6.0 with a 13.0.9.0-r1 Dashboard also offers the **second** MCP server type:
REST API operations from a deployed integration runtime as MCP tools, which is the
containerized twin of demo 2. Deploying the `CustomerApi` BAR here and exposing it through
the Dashboard shows the same capability driven by a UI instead of `mcptools.json`. It needs
operator 13.3.0 or later and Dashboard 13.0.8.0-r1 or later, so this setup already clears
it.

## Gotchas

- An operator version below 12.20.0 hides the MCP servers page entirely. Below 13.3.0 only
  the connector type is offered.
- The license id changed. `L-KPRV-AUG9NC` is correct for operator 12.1x and wrong here.
- A reservation expires. The kubeconfig context from an expired cluster still sits in your
  local config and fails with "you must be logged in to the server", which looks like a
  credential problem rather than an expiry.
- The content server only exists when storage is attached. With `spec.storage.type: none`
  there is no BAR upload, which would rule out the bonus above.

## Teardown

Nothing to undo: the reservation expires and the cluster is destroyed with it. Delete the
reservation early if you finish early, since it is shared capacity. Afterwards, drop the
stale context locally:

```bash
oc config delete-context <context-name>
```
