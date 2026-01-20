# Atomstream Auth

Based on Oak.


```
docker compose up -d oak-postgres oak-redis
```

Questions

- Will I launch one instance, or multiple instances?
  - One for Atomstream
  - One per forge
- How do I migrate users?
- How about authorization?
- What is the user ID like?


Initial setup

```
bin/atomauth jwk create
bin/atomauth user create --email kyle@atomstream.io --password securepassword123
bin/atomauth oauth-client create --help
bin/atomauth oauth-client create --client-name "my-first-client"
bin/atomauth oauth-client create --client-name "foo" --redirect-uri 'https://example.com/redirect' --scope email --scope openid --scope offline_access
bin/oakadm user create --email foo@bar.com --password abc
```

