import { useQuery } from "@tanstack/react-query";
import { api, type Me, type Role } from "./api";

export const roleHome: Record<Role, string> = {
  WORKER: "/user/overview",
  PUBLISHER: "/user/overview",
  ADMIN: "/user/admin/overview",
};

export const roleLabel: Record<Role, string> = {
  WORKER: "Worker",
  PUBLISHER: "Publisher",
  ADMIN: "Admin",
};

export function useMe() {
  return useQuery<Me>({
    queryKey: ["me"],
    queryFn: () => api.me(),
    retry: false,
    staleTime: 30_000,
  });
}
