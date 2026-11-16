import { Component } from 'react'

export default class ErrorBoundary extends Component {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div className="state state-full state-error" role="alert">
        <strong>Something went wrong</strong>
        <span>An unexpected error occurred. Reloading the page usually fixes it.</span>
        <button type="button" className="btn btn-primary" onClick={() => window.location.reload()}>
          Reload page
        </button>
      </div>
    )
  }
}
