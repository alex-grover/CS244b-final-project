var app = angular.module('main', ['ngTable']).
  controller('ShardCtrl', function($scope, $http, ngTableParams) {
    $scope.meta = {'shard': '(unknown shardid)',
                   'files': []};

    $scope.tableParams = new ngTableParams({
        page: 1,            // show first page
        count: 10           // count per page
    }, {
        total: $scope.meta.files.length, // length of data
        getData: function($defer, params) {
            $defer.resolve($scope.meta.files.slice((params.page() - 1) * params.count(), params.page() * params.count()));
        }
    });

    $scope.refresh = function() {
        $http.get('/api/shard/meta').success(function(data, status, headers, config) {
            $scope.meta = data;
        });
    };

    $scope.refresh();
});
