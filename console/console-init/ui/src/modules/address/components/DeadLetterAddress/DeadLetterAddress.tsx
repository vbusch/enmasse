/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

import React from "react";
import { StyleSheet } from "@patternfly/react-styles";
import { IDropdownOption, DropdownWithToggle } from "components";
import { FormGroup, DropdownPosition } from "@patternfly/react-core";

const styles = StyleSheet.create({
  dropdownItem: {
    "text-transform": "capitalize",
    fontWeight: "bold"
  },
  dropdown_align: {
    display: "flex"
  },
  dropdown_toggle: {
    flex: "1"
  }
});
interface IDeadLetterAddressProps {
  onDeadletterSelect?: (value: string) => void;
  deadletterAddress: string;
  deadletterOptions?: IDropdownOption[];
}
export const DeadLetterAddress: React.FunctionComponent<IDeadLetterAddressProps> = ({
  deadletterAddress,
  onDeadletterSelect,
  deadletterOptions
}) => {
  return (
    <FormGroup
      label="Deadletter Address"
      isRequired={false}
      fieldId="addr-configuration-deadletter-dropdown"
    >
      <br />
      <DropdownWithToggle
        id="addr-configuration-deadletter-dropdown"
        className={styles.dropdown_align}
        toggleClass={styles.dropdown_toggle}
        dropdownItemClass={styles.dropdownItem}
        position={DropdownPosition.left}
        onSelectItem={onDeadletterSelect}
        value={deadletterAddress}
        dropdownItems={deadletterOptions}
        dropdownItemId="address-definition-topic-dropdown-item"
        isDisplayLabelAndValue={true}
        isRequired={false}
      />
      <br />
    </FormGroup>
  );
};
