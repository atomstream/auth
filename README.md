# Atomstream Auth

This project uses [Oak][oak-forge], a recently released Clojure-based
identity provider (IdP), as a library.

The Gaiwan team closed their [announcement][oak-ann] blog post post with

	"[As a Clojure user, you] can embed [Oak] into your application, so you
	get login pages, password reset emails, 2FA, and more. This use case
	isn't documented or well developed yet, but it's an interesting
    secondary purpose for Oak that we're excited about".

This is precisely the sort of thing we are interested in exploring,
and we share the excitement :)

[oak-ann]: https://gaiwan.co/blog/announcing-oak-1-0/
[oak-forge]: https://git.gaiwan.co/gaiwan/Oak

## Why a custom IdP?

As a Clojure consultancy, Atomstream is interested in providing access
to self-hosted services via a centralized IAM/IdP solution. We believe
this gets us three very important things:

1. It lowers friction for our end users, since they have to remember
   fewer logins. (If we figure out how to federate with Google or
   Microsoft, they don't have to remember any logins at all!)

2. It provides us a stable user identifier for data pipelines,
   unlocking value in undersatnding user behavior across tools.

3. It provides our customers with a core auditable component, building 
   trust.

We are particularly interested in integrating
[Tailscale](https://tailscale.com/) and an in-house OIDC IdP, so that
our staff, users, and possibly customers can be automatically logged
to a selection of best-in-class tools when they connect.

(NB: It seems we will need to add a 
[Webfinger](https://tailscale.com/docs/integrations/identity/custom-oidc)
endpoint for Tailscale integration; probably an easy task.)

## Where is this headed?

At present, this project does not implement any additional features
beyond those already present in Oak. In the near future, we would like to add:

- Custom branding
- i18n
- Customized account emails
- Webfinger implementation for Tailscale
- Metrics using Prometheus
- Service discovery with Traefik
- Authorization (big topic)
- Passkeys

We intend to will keep this project's open source status and license
the same as that of Oak.

## Dependencies

Same as Oak:

- Clojure 1.12
- babashka

Useful for development: Docker

## Developing

Launch the application using launchpad:

```
echo '{:launchpad/aliases [:dev] :launchpad/options {:go true}}' > deps.local.edn
bin/launchpad
```

## Example client

This project includes an example client which uses the same
dependencies as Oak,with the exception of `lambdaisland.cli` (for
now). Why the same dependenceis?  If we are going to build on the
library, we should understand how it works. No better way to
understand it than to pick apart some components and put them back
together in the form of a client app. That, and the lambdaisland
collection of libraries are stable and well integrated, so this may a
good base for building larger applications.

To run the example, you will need to create a client and user.

```
bin/atomauth jwk create
bin/atomauth user create --email kyle@atomstream.io --password password123
bin/atomauth oauth-client create --client-name "example" --redirect-uri \ 'http://localhost:8080/callback' --scope profile --scope openid --scope email
```

Start the application

```
cd example
clojure -M -m auth-example.cli --client-id "your-id" --client-secret "your-secret"
```
Go to http://localhost:8080 . You should see a welcome page with a login link.

