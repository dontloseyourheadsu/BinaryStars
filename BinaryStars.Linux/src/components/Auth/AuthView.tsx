import { FormEvent, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
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
  const [oauthHint, setOauthHint] = useState("");

  const readApiError = (error: unknown): string => {
    if (typeof error === "string" && error.trim().length > 0) {
      return error;
    }

    if (error instanceof Error && error.message.trim().length > 0) {
      return error.message;
    }

    if (typeof error !== "object" || error === null) {
      return "Authentication failed";
    }

    const candidate = error as { response?: { data?: unknown }; message?: string };
    const payload = candidate.response?.data;
    if (Array.isArray(payload) && payload.length > 0) {
      const first = payload[0];
      if (typeof first === "string" && first.trim().length > 0) {
        return first;
      }
    }

    return candidate.message ?? "Authentication failed";
  };

  const getProviderToken = async (provider: "google" | "microsoft"): Promise<string> => {
    const googleClientId = (import.meta.env.VITE_GOOGLE_CLIENT_ID ?? "").trim();
    const googleRedirectUri = (import.meta.env.VITE_GOOGLE_REDIRECT_URI ?? "").trim();
    const googleClientSecret = (import.meta.env.VITE_GOOGLE_CLIENT_SECRET ?? "").trim();
    const microsoftClientId = (import.meta.env.VITE_MICROSOFT_CLIENT_ID ?? "").trim();
    const microsoftTenantId = (import.meta.env.VITE_MICROSOFT_TENANT_ID ?? "common").trim();
    const microsoftScope = (import.meta.env.VITE_MICROSOFT_SCOPE ?? "").trim();
    const microsoftRedirectUri = (
      import.meta.env.VITE_MICROSOFT_REDIRECT_URI ?? googleRedirectUri
    ).trim();

    if (provider === "google") {
      if (!googleClientId || !googleRedirectUri) {
        throw new Error("Google OAuth is not configured. Set VITE_GOOGLE_CLIENT_ID and VITE_GOOGLE_REDIRECT_URI.");
      }

      return invoke<string>("oauth_get_provider_token", {
        provider,
        clientId: googleClientId,
        redirectUri: googleRedirectUri,
        googleClientSecret,
      });
    }

    if (!microsoftClientId || !microsoftRedirectUri) {
      throw new Error(
        "Microsoft OAuth is not configured. Set VITE_MICROSOFT_CLIENT_ID and VITE_MICROSOFT_REDIRECT_URI.",
      );
    }

    return invoke<string>("oauth_get_provider_token", {
      provider,
      clientId: microsoftClientId,
      redirectUri: microsoftRedirectUri,
      microsoftTenantId,
      microsoftScope:
        microsoftScope || `api://${microsoftClientId}/access_as_user openid profile email offline_access`,
    });
  };

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
    setBusy(true);
    setError("");
    setOauthHint("Check your browser to continue sign-in, then return to BinaryStars.");
    let providerToken = "";
    try {
      providerToken = (await getProviderToken(provider)).trim();
      const auth = await api.externalLogin(provider, providerToken);

      tokenStore.setToken(auth.accessToken, auth.expiresIn);
      onLoggedIn();
    } catch (error) {
      const message = readApiError(error);

      if (message.toLowerCase().includes("registration required")) {
        const username = window.prompt("Choose a username to create your account:", "");
        if (!username || !username.trim()) {
          setError("Username is required to complete registration.");
        } else {
          try {
            const auth = await api.externalLogin(provider, providerToken, username.trim());
            tokenStore.setToken(auth.accessToken, auth.expiresIn);
            onLoggedIn();
          } catch (innerError) {
            setError(readApiError(innerError));
          }
        }
      } else {
        setError(message || "External login failed");
      }
    } finally {
      setOauthHint("");
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
        {oauthHint && <p>{oauthHint}</p>}
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
