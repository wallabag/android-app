document.addEventListener("DOMContentLoaded", function() {
    const app = new annotator.App();

    app.include(annotator.ui.main, {
        element: document.querySelector('article'),
    });

    const authorization = {
        permits: function() { return true; },
    };
    app.registry.registerUtility(authorization, 'authorizationPolicy');

    const myStorage = function () {
        function trace(action, annotation) {
            var copyAnno = JSON.parse(JSON.stringify(annotation));
            console.debug("annotator.storage.debug: " + action, copyAnno);
        }

        return {
            create: function (annotation) {
                trace('create', annotation);
                return JSON.parse(hostAnnotationController.createAnnotation(JSON.stringify(annotation)));
            },

            update: function (annotation) {
                trace('update', annotation);
                return JSON.parse(hostAnnotationController.updateAnnotation(JSON.stringify(annotation)));
            },

            'delete': function (annotation) {
                trace('destroy', annotation);
                return JSON.parse(hostAnnotationController.deleteAnnotation(JSON.stringify(annotation)));
            },

            query: function (queryObj) {
                trace('query', queryObj);
                const a = JSON.parse(hostAnnotationController.getAnnotations());
                return {results: a, meta: {total: a.length}};
            },

            configure: function (registry) {
                registry.registerUtility(this, 'storage');
            }
        };
    };

    app.include(myStorage);

    app.start().then(function() {
        app.annotations.load({});
    });
});


function invokeAnnotator() {
    console.log('invokeAnnotator() started');
    var sel = document.getSelection().getRangeAt(0).cloneRange().getClientRects()[0];
    var x = sel.x + sel.width/2;
    var y = sel.y + sel.height/2;
    console.log('invokeAnnotator() coords: x=' + x + ', y=' + y);

    var element = document.querySelector('article');
    var clickEvent = document.createEvent('MouseEvents');
    clickEvent.initMouseEvent('mouseup', true, true, document.defaultView,
                0, x, y, x, y,
                false, false, false, false, 0, element);
    console.log('invokeAnnotator() dispatching event');
    element.dispatchEvent(clickEvent);
    console.log('invokeAnnotator() event dispatched');
}
