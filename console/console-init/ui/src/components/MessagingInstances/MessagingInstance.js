

class MessagingInstance {
  constructor(name, namespace, component, type, timeCreated, phase, url) {
    this.name = name;
    this.namespace = namespace;
    this.component = 'AS';
    this.type = type;
    this.timeCreated = timeCreated;
    this.consoleUrl = url;
    this.phase = phase;
  }

}

export default MessagingInstance;
