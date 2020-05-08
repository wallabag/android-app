document.addEventListener("DOMContentLoaded", function(event) {
    const observer = new MutationObserver(function(mutations) {
        const node = mutations[0].addedNodes[0];
        if (node && node.nodeName === "STYLE") {
            node.remove();
            observer.disconnect();
        }
    });
    observer.observe(document.head, {childList: true});
});
