import { useQuery } from "@tanstack/react-query";
import AppLayout from "@cloudscape-design/components/app-layout";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import SideNavigation from "@cloudscape-design/components/side-navigation";
import Table from "@cloudscape-design/components/table";
import TopNavigation from "@cloudscape-design/components/top-navigation";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Badge from "@cloudscape-design/components/badge";
import { listContracts, type Contract } from "./api";

const navigationItems = [
  { type: "link" as const, text: "Contracts", href: "#/contracts" },
  { type: "link" as const, text: "Submissions", href: "#/submissions" },
  { type: "link" as const, text: "Leaderboard", href: "#/leaderboard" },
  { type: "link" as const, text: "New builds", href: "#/builds" },
];

export default function App() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["contracts"],
    queryFn: () => listContracts(),
  });

  return (
    <>
      <TopNavigation
        identity={{ href: "#", title: "Symmetry Console" }}
        utilities={[
          { type: "button", text: "Docs", href: "#/docs" },
          { type: "button", text: "Sign in" },
        ]}
      />
      <AppLayout
        navigation={
          <SideNavigation
            header={{ href: "#/", text: "Symmetry" }}
            items={navigationItems}
          />
        }
        contentType="table"
        toolsHide
        content={
          <ContentLayout
            header={
              <Header
                variant="h1"
                description="Published job cards. Each contract is a machine-readable task for workers."
                actions={
                  <SpaceBetween direction="horizontal" size="xs">
                    <Button onClick={() => refetch()}>Refresh</Button>
                    <Button variant="primary">Publish contract</Button>
                  </SpaceBetween>
                }
              >
                Contracts
              </Header>
            }
          >
            <Table<Contract>
              loading={isLoading}
              loadingText="Loading contracts"
              items={data?.content ?? []}
              trackBy="id"
              empty={
                <Box textAlign="center" color="inherit">
                  <b>No contracts yet</b>
                  <Box variant="p" color="inherit">
                    {isError
                      ? "Could not reach the API. Is the platform running?"
                      : "Publish a contract to get started."}
                  </Box>
                </Box>
              }
              columnDefinitions={[
                { id: "moduleId", header: "Module", cell: (c) => c.moduleId },
                { id: "version", header: "Version", cell: (c) => c.version },
                {
                  id: "taskKind",
                  header: "Task kind",
                  cell: (c) => <Badge>{c.taskKind}</Badge>,
                },
                { id: "workKind", header: "Work kind", cell: (c) => c.workKind },
                { id: "language", header: "Language", cell: (c) => c.language },
                {
                  id: "lifecycleState",
                  header: "State",
                  cell: (c) => c.lifecycleState,
                },
                {
                  id: "disclosureTier",
                  header: "Tier",
                  cell: (c) => c.disclosureTier,
                },
              ]}
            />
          </ContentLayout>
        }
      />
    </>
  );
}
