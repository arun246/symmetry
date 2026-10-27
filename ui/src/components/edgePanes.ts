// Both edge panes listen for Escape on the document. Without a claim, one Escape
// would close the navigation drawer and the instructions pane at the same time.
// Panes are kept in the order they were opened; Escape belongs to the newest one.
// Closing that pane must not hand Escape to nobody while an older pane is still
// open, so "who owns Escape" is a stack, not a single slot.
const open: string[] = [];

export function claimEdgePane(id: string) {
  const existing = open.indexOf(id);
  if (existing >= 0) open.splice(existing, 1);
  open.push(id);
}

export function releaseEdgePane(id: string) {
  const existing = open.indexOf(id);
  if (existing >= 0) open.splice(existing, 1);
}

export function ownsEdgePane(id: string) {
  return open.length > 0 && open[open.length - 1] === id;
}
