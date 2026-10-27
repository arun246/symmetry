import { useEffect, useId, useRef, type ReactNode } from "react";
import { EdgeTab } from "./EdgeTab";
import { claimEdgePane, ownsEdgePane, releaseEdgePane } from "./edgePanes";

// Workspace instructions: docked, not an overlay. Opening it narrows the editor;
// closing it gives the full width back. Below 900px CSS turns it into a slide-in
// pane because a split is unusable on a phone.
export function DockPane({
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
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, onOpenChange, paneId]);

  return (
    <div className={`dock-pane${open ? " open" : ""}`}>
      <aside id={panelId} className="dock-surface" aria-hidden={!open} tabIndex={-1}>
        {children}
      </aside>
      <div className="dock-rail">
        <EdgeTab
          ref={tabRef}
          side="right"
          label={label}
          open={open}
          controls={panelId}
          variant="dock"
          onClick={() => onOpenChange(!open)}
        />
      </div>
    </div>
  );
}
