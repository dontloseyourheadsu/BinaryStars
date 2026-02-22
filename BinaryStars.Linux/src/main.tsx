import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./design-tokens.css";

// App.css imports remain (App.tsx imports App.css) so tokens apply first
import 'leaflet/dist/leaflet.css';

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
