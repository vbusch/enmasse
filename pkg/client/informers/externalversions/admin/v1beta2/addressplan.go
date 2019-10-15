/*
 * Copyright 2018-2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

// Code generated by informer-gen. DO NOT EDIT.

package v1beta2

import (
	time "time"

	adminv1beta2 "github.com/enmasseproject/enmasse/pkg/apis/admin/v1beta2"
	versioned "github.com/enmasseproject/enmasse/pkg/client/clientset/versioned"
	internalinterfaces "github.com/enmasseproject/enmasse/pkg/client/informers/externalversions/internalinterfaces"
	v1beta2 "github.com/enmasseproject/enmasse/pkg/client/listers/admin/v1beta2"
	v1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	runtime "k8s.io/apimachinery/pkg/runtime"
	watch "k8s.io/apimachinery/pkg/watch"
	cache "k8s.io/client-go/tools/cache"
)

// AddressPlanInformer provides access to a shared informer and lister for
// AddressPlans.
type AddressPlanInformer interface {
	Informer() cache.SharedIndexInformer
	Lister() v1beta2.AddressPlanLister
}

type addressPlanInformer struct {
	factory          internalinterfaces.SharedInformerFactory
	tweakListOptions internalinterfaces.TweakListOptionsFunc
	namespace        string
}

// NewAddressPlanInformer constructs a new informer for AddressPlan type.
// Always prefer using an informer factory to get a shared informer instead of getting an independent
// one. This reduces memory footprint and number of connections to the server.
func NewAddressPlanInformer(client versioned.Interface, namespace string, resyncPeriod time.Duration, indexers cache.Indexers) cache.SharedIndexInformer {
	return NewFilteredAddressPlanInformer(client, namespace, resyncPeriod, indexers, nil)
}

// NewFilteredAddressPlanInformer constructs a new informer for AddressPlan type.
// Always prefer using an informer factory to get a shared informer instead of getting an independent
// one. This reduces memory footprint and number of connections to the server.
func NewFilteredAddressPlanInformer(client versioned.Interface, namespace string, resyncPeriod time.Duration, indexers cache.Indexers, tweakListOptions internalinterfaces.TweakListOptionsFunc) cache.SharedIndexInformer {
	return cache.NewSharedIndexInformer(
		&cache.ListWatch{
			ListFunc: func(options v1.ListOptions) (runtime.Object, error) {
				if tweakListOptions != nil {
					tweakListOptions(&options)
				}
				return client.AdminV1beta2().AddressPlans(namespace).List(options)
			},
			WatchFunc: func(options v1.ListOptions) (watch.Interface, error) {
				if tweakListOptions != nil {
					tweakListOptions(&options)
				}
				return client.AdminV1beta2().AddressPlans(namespace).Watch(options)
			},
		},
		&adminv1beta2.AddressPlan{},
		resyncPeriod,
		indexers,
	)
}

func (f *addressPlanInformer) defaultInformer(client versioned.Interface, resyncPeriod time.Duration) cache.SharedIndexInformer {
	return NewFilteredAddressPlanInformer(client, f.namespace, resyncPeriod, cache.Indexers{cache.NamespaceIndex: cache.MetaNamespaceIndexFunc}, f.tweakListOptions)
}

func (f *addressPlanInformer) Informer() cache.SharedIndexInformer {
	return f.factory.InformerFor(&adminv1beta2.AddressPlan{}, f.defaultInformer)
}

func (f *addressPlanInformer) Lister() v1beta2.AddressPlanLister {
	return v1beta2.NewAddressPlanLister(f.Informer().GetIndexer())
}