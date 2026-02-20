import { FormEvent, useState } from "react";
import { api, tokenStore } from "../../api";

type Props = {
  onLoggedIn: () => void;
  busy: boolean;
  setBusy: (value: boolean) => void;
  setError: (value: string) => void;
};

export default function AuthView({ onLoggedIn, busy, setBusy, setError }: Props) {
  const [registerMode, setRegisterMode] = useState(false);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      if (registerMode) {
        const auth = await api.register(username.trim(), email.trim(), password);
        tokenStore.setToken(auth.accessToken, auth.expiresIn);
      } else {
        const auth = await api.login(email.trim(), password);
        tokenStore.setToken(auth.accessToken, auth.expiresIn);
      }
      onLoggedIn();
    } catch (error) {
      setError(error instanceof Error ? error.message : "Authentication failed");
    } finally {
      setBusy(false);
    }
  };

  const externalLogin = async (provider: "google" | "microsoft") => {
    const token = window.prompt(
      `Paste ${provider} OAuth token. Configure OAuth in README first and use a browser flow to obtain a token.`,
    );
    if (!token) {
      return;
    }
    setBusy(true);
    setError("");
    try {
      const auth = await api.externalLogin(provider, token.trim());
      tokenStore.setToken(auth.accessToken, auth.expiresIn);
      onLoggedIn();
    } catch (error) {
      setError(error instanceof Error ? error.message : "External login failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <h1>BinaryStars</h1>
        <p>{registerMode ? "Create your account" : "Sign in to continue"}</p>
        <form onSubmit={submit}>
          {registerMode && (
            <label>
              Username
              <input
                aria-label="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                type="text"
              />
            </label>
          )}
          <label>
            Email
            <input
              aria-label="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              type="text"
            />
          </label>
          <label>
            Password
            <input
              aria-label="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              type="password"
            />
          </label>
          <button disabled={busy} type="submit">
            {registerMode ? "Register" : "Sign In"}
          </button>
        </form>
        <div className="oauth-row">
          <button disabled={busy} onClick={() => externalLogin("google")} type="button">
            Continue with Google
          </button>
          <button disabled={busy} onClick={() => externalLogin("microsoft")} type="button">
            Continue with Microsoft
          </button>
        </div>
        <button
          className="text-btn"
          onClick={() => setRegisterMode((value) => !value)}
          type="button"
        >
          {registerMode ? "Already have an account? Login" : "Don’t have an account? Register"}
        </button>
      </section>
    </main>
  );
}
