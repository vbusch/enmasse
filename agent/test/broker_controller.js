/*
 * Copyright 2017 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

var assert = require('assert');
var util = require('util');
var MockBroker = require('../testlib/mock_broker.js');
var broker_controller = require('../lib/broker_controller.js');

function random_number(max) {
    return Math.floor(Math.random() * Math.floor(max));
}

describe('broker controller', function() {
    var broker;
    var controller;

    beforeEach(function(done) {
        broker = new MockBroker('mybroker');
        broker.listen().on('listening', function () {
            controller = broker_controller.create_agent();
            controller.connect({port:broker.port});
            controller.on('ready', function () {
                done();
            });
        });
    });

    afterEach(function(done) {
        broker.close();
        controller.close().then(() => done());
    });

    it('creates a queue', function(done) {
        controller.addresses_defined([{address:'foo',type:'queue'}]).then(function () {
            var addresses = broker.list_addresses();
            var queues = broker.list_queues();
            broker.verify_queue(addresses, queues, 'foo');
            assert.equal(addresses.length, 0);
            assert.equal(queues.length, 0);
            done();
        });
    });
    it('creates a topic', function(done) {
        controller.addresses_defined([{address:'bar',type:'topic'}]).then(function () {
            var addresses = broker.list_addresses();
            broker.verify_topic(addresses, 'bar');
            assert.equal(addresses.length, 0);
            done();
        });
    });
    it('deletes a queue', function(done) {
        controller.addresses_defined([{address:'foo',type:'queue'}, {address:'bar',type:'topic'}]).then(function () {
            var addresses = broker.list_addresses();
            var queues = broker.list_queues();
            broker.verify_queue(addresses, queues, 'foo');
            broker.verify_topic(addresses, 'bar');
            assert.equal(addresses.length, 0);
            assert.equal(queues.length, 0);
            controller.addresses_defined([{address:'bar',type:'topic'}]).then(function () {
                var addresses = broker.list_addresses();
                var queues = broker.list_queues();
                broker.verify_topic(addresses, 'bar');
                assert.equal(addresses.length, 0, util.format('extra addresses: %j', addresses));
                assert.equal(queues.length, 0, util.format('extra queues: %j', queues));
                done();
            }).catch(done);
        }).catch(done);
    });
    it('deletes a topic', function(done) {
        controller.addresses_defined([{address:'foo',type:'queue'}, {address:'bar',type:'topic'}]).then(function () {
            var addresses = broker.list_addresses();
            var queues = broker.list_queues();
            broker.verify_queue(addresses, queues, 'foo');
            broker.verify_topic(addresses, 'bar');
            assert.equal(addresses.length, 0);
            assert.equal(queues.length, 0);
            controller.addresses_defined([{address:'foo',type:'queue'}]).then(function () {
                var addresses = broker.list_addresses();
                var queues = broker.list_queues();
                broker.verify_queue(addresses, queues, 'foo');
                assert.equal(addresses.length, 0);
                assert.equal(queues.length, 0);
                done();
            }).catch(done);
        }).catch(done);
    });
    it('retrieves topic stats', function(done) {
        broker.add_topic_address('foo', {
            's1':{durable:true, messageCount:10, consumerCount:9, messagesAdded:8, deliveringCount:7, messagesAcked:6, messagesExpired:5, messagesKilled: 4},
            's2':{durable:false, messageCount:11, consumerCount:10, messagesAdded:9, deliveringCount:8, messagesAcked:7, messagesExpired:6, messagesKilled: 5},
        }, 21);
        for (var i = 0; i < 5; i++) broker.add_connection({ clientAddress: "example:" + random_number(16384) + 49152}, [{destination:'foo'}]);
        Promise.all([
            new Promise(function (resolve, reject) {
                controller.on('address_stats_retrieved', function (stats) {
                    resolve(stats);
                });
            }),
            controller.retrieve_stats()
        ]).then(function (results) {
            var stats = results[0];
            assert.equal(stats.foo.propagated, 100);
            assert.equal(stats.foo.messages_in, 17);
            assert.equal(stats.foo.messages_out, 13);
            assert.equal(stats.foo.receivers, 2);
            assert.equal(stats.foo.senders, 5);
            done();
        }).catch(done);
    });
    function generate_address_list(count, allowed_types) {
        var types = allowed_types || ['anycast', 'multicast', 'queue', 'topic'];
        var list = [];
        for (var i = 0; i < count; i++) {
            list.push({address:util.format('address-%s', (i+1)), type:types[i % types.length]});
        }
        return list;
    }

    it('creates lots of queues', function(done) {
        this.timeout(15000);
        var desired = generate_address_list(2000, ['queue']);
        controller._sync_addresses(desired).then(function () {
            controller.close();
            broker.verify_addresses(desired);
            done();
        }).catch(done);
    });
    it('creates lots of topics', function(done) {
        this.timeout(15000);
        var desired = generate_address_list(2000, ['topic']);
        controller._sync_addresses(desired).then(function () {
            controller.close();
            broker.verify_addresses(desired);
            done();
        }).catch(done);
    });
    it('creates lots of queues and topics', function(done) {
        this.timeout(15000);
        var desired = generate_address_list(2000, ['queue', 'topic']);
        controller._sync_addresses(desired).then(function () {
            controller.close();
            broker.verify_addresses(desired);
            done();
        }).catch(done);
    });

    var config = { BROKER_GLOBAL_MAX_SIZE: "64MB" };

    it('get address settings async - returns setting maxSizeBytes calculated from infra', function(done) {
        config.BROKER_GLOBAL_MAX_SIZE="64MB";
        var brokerAddressSettings = new broker_controller.BrokerController(undefined, config);
        brokerAddressSettings.get_address_settings_async({address:'foo',type:'queue', plan: 'small-queue', status: {planStatus: {name: "small-queue", resources: {broker: 0.2}}}}).then(function (result) {
            assert.equal(13421773, result.maxSizeBytes);
            done();
        });
    });

    it('get address settings async - returns setting maxSizeBytes calculated from infra with partitions', function(done) {
        config.BROKER_GLOBAL_MAX_SIZE="64MB";
        var brokerAddressSettings =  new broker_controller.BrokerController(undefined, config);
        brokerAddressSettings.get_address_settings_async({address:'foo',type:'queue', plan: 'small-queue', status: {planStatus: {name: "small-queue", partitions: 2, resources: {broker: 0.2}}}}).then(function (result) {
            assert.equal(6710886, result.maxSizeBytes);
            done();
        });
    });

    it('get address settings async - returns setting maxSizeBytes calculated from broker', function(done) {
        config.BROKER_GLOBAL_MAX_SIZE=undefined;
        var brokerAddressSettings =  new broker_controller.BrokerController(undefined, config);
        brokerAddressSettings.get_address_settings_async({address:'foo',type:'queue', plan: 'small-queue', status: {planStatus: {name: "small-queue", resources: {broker: 0.1}}}}, Promise.resolve(1000000)).then(function (result) {
            assert.equal(100000, result.maxSizeBytes);
            done();
        });
    });

    it('get address settings async - returns ttl settings', function(done) {
        var brokerAddressSettings =  new broker_controller.BrokerController(undefined, config);
        brokerAddressSettings.get_address_settings_async({address:'foo',type:'queue', plan: 'small-queue', status: {messageTtl: {minimum: 1000, maximum: 2000}}}, Promise.resolve(undefined)).then(function (result) {
            assert.equal(1000, result.minExpiryDelay);
            assert.equal(2000, result.maxExpiryDelay);
            done();
        });
    });

});
