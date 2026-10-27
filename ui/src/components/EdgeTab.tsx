import { forwardRef } from "react";

// A Chrome-style tab, rotated 90°. The button stays in the DOM while the pane is
// open so the user always has a visible way to close it again.
export const EdgeTab = forwardRef<
  HTMLButtonElement,
  {
    side: "left" | "right";
    label: string;
    open: boolean;
    controls: string;
    /** "overlay" floats above content; "dock" sits in the workspace split rail. */
    variant?: "overlay" | "dock";
    onClick: () => void;
  }
>(function EdgeTab({ side, label, open, controls, variant = "overlay", onClick }, ref) {
  const chevron = side === "left" ? (open ? "‹" : "›") : open ? "›" : "‹";

  return (
    <button
      ref={ref}
      type="button"
      className={`edge-tab edge-tab-${side} edge-tab-${variant}${open ? " open" : ""}`}
      aria-expanded={open}
      aria-controls={controls}
      aria-label={`${open ? "Hide" : "Show"} ${label.toLowerCase()}`}
      onClick={onClick}
    >
      <span className="edge-tab-chevron" aria-hidden="true">
        {chevron}
      </span>
      <span className="edge-tab-text">{label}</span>
    </button>
  );
});
