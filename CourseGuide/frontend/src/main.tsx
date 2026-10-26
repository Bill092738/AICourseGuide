import React from "react"
import ReactDOM from "react-dom/client"
import "./material"
import "./index.css"
import App from "./App"
import { SnackbarProvider } from "./components/Snackbar"
import { AppStateProvider } from "./state/AppState"
import { applyTheme, loadThemePrefs } from "./theme/theme"

// Apply the color scheme before the first render to avoid a flash of unthemed content.
applyTheme(loadThemePrefs())

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <SnackbarProvider>
      <AppStateProvider>
        <App />
      </AppStateProvider>
    </SnackbarProvider>
  </React.StrictMode>,
)
