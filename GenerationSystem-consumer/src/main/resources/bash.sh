#  生成 noar edf
"C:\Program Files (x86)\Persyst\Insight\PSCLI.exe" /Archive /SourceFile="C:/Users/Dell6Core/Desktop/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1.lay" /FileType=XLTEK /OutputFile="C:/Users/Dell6Core/Desktop/output_noar.edf"

# process
"C:\Program Files (x86)\Persyst\Insight\PSCLI.exe" /Process /MMX="C:\ProgramData\Persyst\Trend Settings Research - Export P14.mmx" /SourceFile="C:/Users/Dell6Core/Desktop/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1.lay"

# 生成 ar edf
"C:\Program Files (x86)\Persyst\Insight\PSCLI.exe" /Archive /SourceFile="C:/Users/Dell6Core/Desktop/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1.lay" /FileType=XLTEK /OutputFile="C:/Users/Dell6Core/Desktop/output_ar.edf"

# 生成 ar csv
reg add HKEY_CURRENT_USER\Software\Persyst\PSMMarker\Settings /v _ForceRawTrends /t REG_DWORD /d 0x00000000 /f
"C:/Program Files (x86)/Persyst/Insight/PSCLI.exe" /panel="Research - Export" /SourceFile="C:/Users/Dell6Core/Desktop/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1.lay" /MMX="C:/ProgramData/Persyst/Trend Settings Research - Export P14.mmx" /ExportCSV /OutputFile="C:/Users/Dell6Core/Desktop/output_ar.csv"
# 生成 noar csv
reg add HKEY_CURRENT_USER\Software\Persyst\PSMMarker\Settings /v _ForceRawTrends /t REG_DWORD /d 0x00000001 /f
"C:/Program Files (x86)/Persyst/Insight/PSCLI.exe" /panel="Research - Export" /SourceFile="C:/Users/Dell6Core/Desktop/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1/x~ x_52b9dd29-e0f8-4a68-acf0-1141ab22b4d1.lay" /MMX="C:/ProgramData/Persyst/Trend Settings Research - Export P14.mmx" /ExportCSV /OutputFile="C:/Users/Dell6Core/Desktop/output_noar.csv"

# rbm
docker run -it --rm --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4.0-management
docker run -it --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4.0-management
