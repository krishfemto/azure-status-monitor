# Azure Status Monitor — Live Service Status Page

**Languages:** [English](README.md) | [日本語](README.jp.md)

---

## What this is

A small monitoring tool that checks if a few websites are up, every 5 minutes, and shows the results on a live status page — similar to the "status" pages companies like GitHub or Slack have for their services.

This was my second Azure project, built to try a different trigger type (Timer instead of HTTP/Queue) and a different storage type (Table Storage instead of Blob).

**Live demo:** https://krishnastatus28355.z11.web.core.windows.net/

## How it works

![Architecture Diagram](architecture-diagram.png)

1. An Azure Function (`StatusCheck`) runs automatically every 5 minutes
2. It checks a list of websites and records whether each one responded, how long it took, and when it was checked
3. Each result is saved as a row in Azure Table Storage
4. A static webpage (hosted on Azure Storage) reads the last 10 results for each site directly from the table and displays them as a status page with a history of green/red dots

## Tech used

- Java 17
- Azure Functions (Timer trigger)
- Azure Table Storage
- Azure Storage Static Website hosting
- Application Insights (automatically attached for monitoring/logs)
- Azure CLI for setting up the resources

## Screenshots

**The live status page**
![Live status page](1-live-status-page.png)

**Raw data in Table Storage**
![Table storage data](2-table-storage-data.png)

**Function App overview**
![Function App overview](3-function-app-overview.png)

**Application Insights (monitoring)**
![Application Insights](4-application-insights.png)

## A few notes on how it's built

- The page reads from Table Storage using a read-only SAS token, scoped to table access only — the storage account key itself is never exposed
- Each check is stored with a reversed timestamp as the row key, so the newest checks naturally sort first
- The "down" status for one of the sites in the screenshots wasn't staged — it was a real result while I was still pointing at the wrong URL, which I fixed afterwards. Left it in because it's a good example of the monitor actually catching something.

## Running it yourself

Set up the resources:

```bash
az group create --name rg-status-monitor --location japaneast

az storage account create --name <storage-name> \
  --resource-group rg-status-monitor --location japaneast --sku Standard_LRS --kind StorageV2

az storage blob service-properties update \
  --account-name <storage-name> --static-website \
  --index-document index.html --404-document index.html

az storage table create --name statuschecks --account-name <storage-name>
```

Copy `local.settings.json.example` to `local.settings.json` and fill in your storage connection string.

Run locally:
```bash
mvn clean package azure-functions:run
```

Then update `index.html` with your storage account name and a SAS token (read-only, table scope), and upload it:
```bash
az storage blob upload --account-name <storage-name> \
  --container-name '$web' --name index.html --file index.html --overwrite
```

## Notes

- Built and tested using Azure's free tier
- The function currently runs locally via Cloud Shell rather than being deployed as a always-on cloud function — deploying it fully is a possible next step

## Author

Krish — [GitHub](https://github.com/krishfemto)
