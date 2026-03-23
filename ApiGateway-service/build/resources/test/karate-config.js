function fn() {
    var webServerPort = karate.properties['webServerPort'];
    var host = karate.properties['host'];
    var protocol = karate.properties['protocol'];
    var serviceBaseUrl = protocol + '://' + host + ':' + webServerPort;
    var randomSeed = Math.floor(Math.random() * 10000);
    return {
        'serviceBaseUrl': serviceBaseUrl,
        'randomSeed': randomSeed
    };
}
