import * as React from "react";
import { useQuery } from "@apollo/react-hooks";
import { RETURN_ADDRESS_LINKS } from "src/Queries/Queries";
import { IAddressLinksResponse } from "src/Types/ResponseTypes";
import { Loading } from "use-patternfly";
import { IClient, ClientList } from "src/Components/AddressDetail/ClientList";
import { getFilteredValue } from "src/Components/Common/ConnectionListFormatter";
import { EmptyLinks } from "src/Components/Common/EmptyLinks";
import { ISortBy } from "@patternfly/react-table";

export interface IAddressLinksListProps {
  page: number;
  perPage: number;
  name?: string;
  namespace?: string;
  addressname?: string;
  type?: string;
  setAddressLinksTotal: (total: number) => void;
}
export const AddressLinksListPage: React.FunctionComponent<IAddressLinksListProps> = ({
  page,
  perPage,
  name,
  namespace,
  addressname,
  type,
  setAddressLinksTotal
}) => {
  const [sortBy, setSortBy] = React.useState<ISortBy>();
  const { loading, error, data } = useQuery<IAddressLinksResponse>(
    RETURN_ADDRESS_LINKS(page, perPage, name, namespace, addressname),
    { pollInterval: 20000 }
  );
  if (loading) return <Loading />;
  if (error) console.log(error);
  console.log(data);
  const { addresses } = data || {
    addresses: { Total: 0, Addresses: [] }
  };

  if (
    addresses &&
    addresses.Addresses.length > 0 &&
    addresses.Addresses[0].Links.Total > 0
  ) {
    setAddressLinksTotal(addresses.Addresses[0].Links.Total);
  }
  const links =
    addresses &&
    addresses.Addresses.length > 0 &&
    addresses.Addresses[0].Links.Total > 0 &&
    addresses.Addresses[0].Links;

  console.log(links);
  let clientRows: IClient[] = addresses.Addresses[0].Links.Links.map(link => ({
    role: link.Spec.Role.toString(),
    containerId: link.Spec.Connection.Spec.ContainerId,
    name: link.ObjectMeta.Name,
    deliveryRate: getFilteredValue(link.Metrics, "enmasse_messages_in"),
    backlog: getFilteredValue(link.Metrics, "enmasse_messages_backlog"),
    connectionName: link.Spec.Connection.ObjectMeta.Name,
    addressSpaceName: name,
    addressSpaceNamespace: namespace,
    addressSpaceType: type
  }));
  const onSort = (_event: any, index: any, direction: any) => {
    setSortBy({ index: index, direction: direction });
  };
  return (
    <>
      {links && links.Total > 0 ? (
        <ClientList rows={clientRows} onSort={onSort} sortBy={sortBy} />
      ) : (
        <EmptyLinks />
      )}
    </>
  );
};
