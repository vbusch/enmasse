/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package state

import (
	"fmt"
)

type NotConnectedError struct {
	router string
}

func (e *NotConnectedError) Error() string {
	return fmt.Sprintf("Not yet connected to router %s", e.router)
}