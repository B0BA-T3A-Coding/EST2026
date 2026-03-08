document.getElementById("analyze-button").onclick = async function () {

    const report = document.getElementById("report");
    report.innerHTML = "Analyzing calendar...";

    try {
        const response = await fetch("/analyze");
        const data = await response.json();

        let output = "";

        for (let key in data) {
            output += "<h3>" + key + "</h3>";
            output += "<p>" + data[key] + "</p>";
        }

        report.innerHTML = output;

    } catch (err) {
        report.innerHTML = "Error connecting to backend.";
    }
    document.getElementById("connect-calendar").onclick = function () {
        window.location.href = "/oauth2/authorization/google";
    };
};