import { useEffect, useId, useRef, type ReactNode } from "react";
import { EdgeTab } from "./EdgeTab";
import { claimEdgePane, ownsEdgePane, releaseEdgePane } from "./edgePanes";

// Navigation drawer: the pane covers content instead of shrinking it, so tables
// and boards keep their width. It is transient — Escape, a click into the page,
// or focus moving into the page closes it and returns focus to the tab.
export function OverlayPane({
  label,
  open,
  onOpenChange,
  children,
}: {
  label: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
}) {
  const panelId = useId();
  const paneId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const asideRef = useRef<HTMLElement>(null);
  const tabRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) {
      releaseEdgePane(paneId);
      return;
    }
    claimEdgePane(paneId);
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || !ownsEdgePane(paneId)) return;
      onOpenChange(false);
      tabRef.current?.focus();
    };
    // "Hidden when not in focus": the drawer behaves like a flyout, so working
    // in the page (clicking or tabbing into it) dismisses it. The wrapper has
    // pointer-events:none, so only the aside and the tab can be the target.
    const onPointerDown = (event: PointerEvent) => {
      if (panelRef.current?.contains(event.target as Node)) return;
      onOpenChange(false);
    };
    const onFocusIn = (event: FocusEvent) => {
      if (panelRef.current?.contains(event.target as Node)) return;
      onOpenChange(false);
    };
    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("focusin", onFocusIn);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("focusin", onFocusIn);
    };
  }, [open, onOpenChange, paneId]);

  // A route change can close the drawer while focus is still on a nav link.
  // Never leave focus inside an aria-hidden pane.
  useEffect(() => {
    if (open) return;
    const active = document.activeElement;
    if (active && asideRef.current?.contains(active)) {
      tabRef.current?.focus();
    }
  }, [open]);

  return (
    <div ref={panelRef} className={`edge-panel edge-panel-left${open ? " open" : ""}`}>
      <aside ref={asideRef} id={panelId} className="edge-surface" aria-hidden={!open} tabIndex={-1}>
        {children}
      </aside>
      <EdgeTab
        ref={tabRef}
        side="left"
        label={label}
        open={open}
        controls={panelId}
        onClick={() => onOpenChange(!open)}
      />
    </div>
  );
}
