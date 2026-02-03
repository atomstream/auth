# Auth example app

After successful OIDC login:
- Create a server-side session
- Store user info and tokens in the session
- Send a session cookie to the browser

On subsequent requests:
- Browser automatically sends the session cookie
- Server looks up the session
- If valid session exists → user is logged in
- If not → redirect to login

## Example Flow

```
User Request → Cookie: session_id=abc123
              ↓
Server: Check session store
       ↓
   Session exists? → Logged in ✓
       ↓
   Access user data, tokens from session
```

