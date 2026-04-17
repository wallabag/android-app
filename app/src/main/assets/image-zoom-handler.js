document.addEventListener("DOMContentLoaded", function() {
    document.addEventListener("click", function(e) {
        var target = e.target;
        if (target.tagName === "IMG" && target.src) {
            e.preventDefault();
            e.stopPropagation();
            hostImageController.onImageClicked(target.src);
        }
    }, true);
});
