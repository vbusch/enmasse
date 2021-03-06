/*
 * Copyright 2018-2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

// Code generated by informer-gen. DO NOT EDIT.

package v1beta2

import (
	internalinterfaces "github.com/enmasseproject/enmasse/pkg/client/informers/externalversions/internalinterfaces"
)

// Interface provides access to all the informers in this group version.
type Interface interface {
	// AddressPlans returns a AddressPlanInformer.
	AddressPlans() AddressPlanInformer
	// AddressSpacePlans returns a AddressSpacePlanInformer.
	AddressSpacePlans() AddressSpacePlanInformer
}

type version struct {
	factory          internalinterfaces.SharedInformerFactory
	namespace        string
	tweakListOptions internalinterfaces.TweakListOptionsFunc
}

// New returns a new Interface.
func New(f internalinterfaces.SharedInformerFactory, namespace string, tweakListOptions internalinterfaces.TweakListOptionsFunc) Interface {
	return &version{factory: f, namespace: namespace, tweakListOptions: tweakListOptions}
}

// AddressPlans returns a AddressPlanInformer.
func (v *version) AddressPlans() AddressPlanInformer {
	return &addressPlanInformer{factory: v.factory, namespace: v.namespace, tweakListOptions: v.tweakListOptions}
}

// AddressSpacePlans returns a AddressSpacePlanInformer.
func (v *version) AddressSpacePlans() AddressSpacePlanInformer {
	return &addressSpacePlanInformer{factory: v.factory, namespace: v.namespace, tweakListOptions: v.tweakListOptions}
}
