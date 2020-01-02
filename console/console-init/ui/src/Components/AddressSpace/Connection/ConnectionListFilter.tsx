import * as React from "react";
import {
  DataToolbarChip,
  DataToolbarGroup,
  DataToolbarFilter,
  DataToolbarItem,
  DataToolbarToggleGroup,
  DataToolbar,
  DataToolbarContent
} from "@patternfly/react-core/dist/js/experimental";
import {
  Dropdown,
  DropdownToggle,
  DropdownItem,
  InputGroup,
  TextInput,
  Button,
  ButtonVariant,
  Badge
} from "@patternfly/react-core";
import { FilterIcon, SearchIcon } from "@patternfly/react-icons";
import useWindowDimensions from "src/Components/Common/WindowDimension";
import { ISortBy } from "@patternfly/react-table";
import { SortForMobileView } from "src/Components/Common/SortForMobileView";

interface IConnectionListFilterProps {
  filterValue?: string | null;
  setFilterValue: (value: string) => void;
  hostnames?: Array<string>;
  setHostnames: (value: Array<string>) => void;
  containerIds?: Array<string>;
  setContainerIds: (value: Array<string>) => void;
  sortValue?: ISortBy;
  setSortValue: (value: ISortBy) => void;
  totalConnections: number;
}
export const ConnectionListFilter: React.FunctionComponent<IConnectionListFilterProps> = ({
  filterValue,
  setFilterValue,
  hostnames,
  setHostnames,
  containerIds,
  setContainerIds,
  totalConnections,
  sortValue,
  setSortValue
}) => {
  const { width } = useWindowDimensions();
  const [inputValue, setInputValue] = React.useState<string | null>();
  const [filterIsExpanded, setFilterIsExpanded] = React.useState(false);
  const onFilterSelect = (event: any) => {
    setFilterValue(event.target.value);
    setFilterIsExpanded(!filterIsExpanded);
  };

  const filterMenuItems = [
    { key: "filterHostName", value: "Hostname" },
    { key: "filterContainer", value: "Container" }
  ];

  const sortMenuItems = [
    { key: "hostname", value: "Hostname", index: 0 },
    { key: "containerId", value: "Container ID", index: 1 },
    { key: "protocol", value: "Protocol", index: 2 },
    { key: "messageIn", value: "Messages In", index: 3 },
    { key: "messageOut", value: "Messages Out", index: 4 },
    { key: "sender", value: "Senders", index: 5 },
    { key: "receiver", value: "Receivers", index: 6 }
  ];

  const onInputChange = (newValue: string) => {
    setInputValue(newValue);
  };
  const onAddInput = (event: any) => {
    if (filterValue)
      if (filterValue === "Container") {
        if (inputValue && inputValue.trim() !== "" && containerIds) {
          if (containerIds.indexOf(inputValue) < 0) {
            setContainerIds([...containerIds, inputValue]);
          }
          setInputValue(null);
        }
      } else if (filterValue === "Hostname") {
        if (inputValue && inputValue.trim() !== "" && hostnames) {
          if (hostnames.indexOf(inputValue) < 0) {
            setHostnames([...hostnames, inputValue]);
          }
          setInputValue(null);
        }
      }
  };

  const onDelete = (
    type: string | DataToolbarChip,
    id: string | DataToolbarChip
  ) => {
    let index;

    switch (type) {
      case "Hostname":
        if (hostnames && id) {
          index = hostnames.indexOf(id.toString());
          if (index >= 0) hostnames.splice(index, 1);
          setHostnames([...hostnames]);
        }
        break;
      case "Container":
        if (containerIds && id) {
          const containers = containerIds;
          index = containerIds.indexOf(id.toString());
          if (index >= 0) containers.splice(index, 1);
          setContainerIds([...containers]);
        }
        break;
    }
  };
  const clearAllFilters = () => {
    setHostnames([]);
    setContainerIds([]);
  };

  const checkIsFilterApplied = () => {
    if (
      (containerIds && containerIds.length > 0) ||
      (hostnames && hostnames.length > 0)
    ) {
      return true;
    }
    return false;
  };

  const toggleGroupItems = (
    <>
      <DataToolbarGroup variant="filter-group">
        <DataToolbarFilter categoryName="Filter">
          <Dropdown
            id="cl-filter-dropdown"
            position="left"
            onSelect={onFilterSelect}
            isOpen={filterIsExpanded}
            toggle={
              <DropdownToggle onToggle={setFilterIsExpanded}>
                <FilterIcon />
                &nbsp;
                {filterValue && filterValue.trim() !== ""
                  ? filterValue
                  : "Filter"}
              </DropdownToggle>
            }
            dropdownItems={filterMenuItems.map(option => (
              <DropdownItem
                key={option.key}
                value={option.value}
                itemID={option.key}
                component={"button"}>
                {option.value}
              </DropdownItem>
            ))}
          />
        </DataToolbarFilter>
        <DataToolbarItem>
          <DataToolbarFilter
            chips={hostnames}
            deleteChip={onDelete}
            categoryName="Hostname">
            {filterValue && filterValue === "Hostname" && (
              <InputGroup>
                <TextInput
                  name="hostname"
                  id="hostname"
                  type="search"
                  aria-label="search input example"
                  placeholder="Filter By Hostname ..."
                  onChange={onInputChange}
                  value={inputValue || ""}
                />
                <Button
                  id="cl-filter-search-btn"
                  variant={ButtonVariant.control}
                  aria-label="search button for search input"
                  onClick={onAddInput}>
                  <SearchIcon />
                </Button>
              </InputGroup>
            )}
          </DataToolbarFilter>
        </DataToolbarItem>
        <DataToolbarItem>
          <DataToolbarFilter
            chips={containerIds}
            deleteChip={onDelete}
            categoryName="Container">
            {filterValue && filterValue === "Container" && (
              <InputGroup>
                <TextInput
                  name="container"
                  id="container"
                  type="search"
                  aria-label="search container"
                  placeholder="Filter By Container ..."
                  onChange={onInputChange}
                  value={inputValue || ""}
                />
                <Button
                  variant={ButtonVariant.control}
                  aria-label="search button for search input"
                  onClick={onAddInput}>
                  <SearchIcon />
                </Button>
              </InputGroup>
            )}
          </DataToolbarFilter>
        </DataToolbarItem>
      </DataToolbarGroup>
    </>
  );
  const toolbarItems = (
    <>
      <DataToolbarToggleGroup
        toggleIcon={
          <>
            <FilterIcon />
            {checkIsFilterApplied() && (
              <Badge key={1} isRead>
                {totalConnections}
              </Badge>
            )}
          </>
        }
        breakpoint="xl">
        {toggleGroupItems}
      </DataToolbarToggleGroup>
      {width < 769 && (
        <SortForMobileView
          sortMenu={sortMenuItems}
          sortValue={sortValue}
          setSortValue={setSortValue}
        />
      )}
    </>
  );
  return (
    <>
      <DataToolbar
        id="data-toolbar-with-filter"
        className="pf-m-toggle-group-container"
        collapseListedFiltersBreakpoint="md"
        clearAllFilters={clearAllFilters}>
        <DataToolbarContent>{toolbarItems}</DataToolbarContent>
      </DataToolbar>
    </>
  );
};
